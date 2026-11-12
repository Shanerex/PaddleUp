package com.paddleup.scoring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.accounts.dto.UserId;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.rooms.dto.ScoringInputsDto;
import com.paddleup.rooms.dto.Violation;
import com.paddleup.rooms.service.ScoringRoomPort;
import com.paddleup.scoring.dto.RuleLogEntryDto;
import com.paddleup.scoring.dto.RuleSetViewDto;
import com.paddleup.scoring.exception.InvalidRuleSetException;
import com.paddleup.scoring.exception.RuleSetConflictException;
import com.paddleup.scoring.model.RuleLogEntry;
import com.paddleup.scoring.model.RuleLogKind;
import com.paddleup.scoring.model.RuleSetRow;
import com.paddleup.scoring.repository.RuleLogRepository;
import com.paddleup.scoring.repository.RuleSetRepository;
import com.paddleup.scoring.util.ReasonText;
import com.paddleup.scoring.util.RuleDiffs;
import com.paddleup.scoring.util.StoredJson;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A room's scoring rules. A room with no row plays its pack-default snapshot (version 0). Every
 * write follows one order: the Commissioner check, insert-if-absent and lock of the room's row, the
 * freeze check against the server clock (recorded even when the caller is then refused as stale),
 * the stale-version check, then validation, save, log and a recompute of the room. The lock
 * serialises two edits and an edit against the freeze. Once the clock has been seen at or past the
 * first match's start the freeze is recorded on the row and holds, so a later reschedule cannot
 * thaw the room.
 */
@Service
public class RuleSetService {

  private final ScoringRoomPort roomPort;
  private final RuleSetRepository sets;
  private final RuleLogRepository log;
  private final FreezePolicy freeze;
  private final ScoringService scoring;
  private final Clock clock;
  private final StoredJson json;

  @Autowired
  RuleSetService(
      ScoringRoomPort roomPort,
      RuleSetRepository sets,
      RuleLogRepository log,
      FreezePolicy freeze,
      ScoringService scoring,
      Clock clock,
      ObjectMapper mapper) {
    this.roomPort = roomPort;
    this.sets = sets;
    this.log = log;
    this.freeze = freeze;
    this.scoring = scoring;
    this.clock = clock;
    this.json = new StoredJson(mapper);
  }

  /** The room's rules as any member sees them; 404 for a non-member. */
  @Transactional(readOnly = true)
  public RuleSetViewDto read(UUID roomId, UserId user) {
    roomPort.access(roomId, user);
    ScoringInputsDto inputs = roomPort.ruleInputs(roomId);
    RuleSetRow row = sets.find(roomId).orElse(null);
    Optional<Instant> freezesAt = freeze.freezesAt(roomId);
    boolean frozen =
        FreezePolicy.frozen(row == null ? null : row.frozenAt(), freezesAt, clock.instant());
    return view(inputs, row, frozen, freezesAt);
  }

  /** Replaces the rule set before the freeze; the set becomes custom. */
  @Transactional(noRollbackFor = {RuleSetConflictException.class, InvalidRuleSetException.class})
  public RuleSetViewDto edit(UUID roomId, UserId user, int version, RuleSetDto rules) {
    Locked locked = lockForWrite(roomId, user, version);
    if (locked.frozen()) {
      throw frozen();
    }
    List<Violation> violations = checkRules(rules, locked.inputs());
    failOn(violations);
    return save(locked, RuleLogKind.EDIT, rules, null, false, user);
  }

  /** Restores the pack-default snapshot exactly, before the freeze. */
  @Transactional(noRollbackFor = {RuleSetConflictException.class, InvalidRuleSetException.class})
  public RuleSetViewDto revert(UUID roomId, UserId user, int version) {
    Locked locked = lockForWrite(roomId, user, version);
    if (locked.frozen()) {
      throw frozen();
    }
    return save(locked, RuleLogKind.REVERT, null, null, false, user);
  }

  /**
   * The only way to change the rules after the freeze: a reason (1 to 500 characters once cleaned)
   * and the Commissioner's attestation that every manager agreed. It is logged with the old and new
   * sets and recomputes every scored match of the room.
   */
  @Transactional(noRollbackFor = {RuleSetConflictException.class, InvalidRuleSetException.class})
  public RuleSetViewDto changeAfterFreeze(
      UUID roomId, UserId user, int version, RuleSetDto rules, String reason, boolean attested) {
    Locked locked = lockForWrite(roomId, user, version);
    if (!locked.frozen()) {
      throw new RuleSetConflictException(
          RuleSetConflictException.NOT_FROZEN, "The rules are not frozen yet; edit them instead");
    }
    String clean = ReasonText.sanitise(reason);
    List<Violation> violations = new ArrayList<>();
    if (clean.isEmpty() || clean.codePointCount(0, clean.length()) > ReasonText.MAX_LENGTH) {
      violations.add(
          new Violation(
              "reason",
              "Give a reason of 1 to " + ReasonText.MAX_LENGTH + " characters, as plain text"));
    }
    if (!attested) {
      violations.add(new Violation("attested", "Confirm that every manager agreed to the change"));
    }
    violations.addAll(checkRules(rules, locked.inputs()));
    failOn(violations);
    return save(locked, RuleLogKind.POST_FREEZE_CHANGE, rules, clean, true, user);
  }

  /** The room's change log, oldest first, for any member; 404 for a non-member. */
  @Transactional(readOnly = true)
  public List<RuleLogEntryDto> log(UUID roomId, UserId user) {
    roomPort.access(roomId, user);
    return log.list(roomId).stream()
        .map(
            e ->
                new RuleLogEntryDto(
                    e.seq(),
                    e.kind().value(),
                    e.actor(),
                    e.occurredAt(),
                    e.reason(),
                    e.attested(),
                    e.before(),
                    e.after(),
                    e.diff()))
        .toList();
  }

  /** The state a write works on, read under the room's row lock. */
  private record Locked(
      UUID roomId, ScoringInputsDto inputs, RuleSetRow row, boolean frozen, Instant now) {}

  /**
   * The Commissioner check, then insert-if-absent and lock, the stale-version check and the freeze
   * check. A freeze seen for the first time is recorded here, so it commits even when the caller is
   * then refused.
   */
  private Locked lockForWrite(UUID roomId, UserId user, int version) {
    roomPort.requireCommissioner(roomId, user);
    Instant now = clock.instant();
    sets.insertIfAbsent(roomId, now);
    RuleSetRow row = sets.lock(roomId).orElseThrow();
    // Read after the lock, so a write that waited for the lock is judged at its own turn.
    now = clock.instant();
    Optional<Instant> freezesAt = freeze.freezesAt(roomId);
    boolean frozen = FreezePolicy.frozen(row.frozenAt(), freezesAt, now);
    if (frozen && row.frozenAt() == null) {
      sets.markFrozen(roomId, freezesAt.orElse(now));
    }
    // After the freeze is recorded, so a stale caller at the freeze instant cannot leave it unseen.
    if (row.version() != version) {
      throw new RuleSetConflictException(
          RuleSetConflictException.STALE_VERSION,
          "The rules changed since you opened them; reload and try again");
    }
    return new Locked(roomId, roomPort.ruleInputs(roomId), row, frozen, now);
  }

  private RuleSetViewDto save(
      Locked locked,
      RuleLogKind kind,
      RuleSetDto rules,
      String reason,
      boolean attested,
      UserId user) {
    UUID roomId = locked.roomId();
    RuleSetDto before = effective(locked.row(), locked.inputs());
    RuleSetDto after = rules == null ? locked.inputs().defaultRules() : rules;
    if (!sets.update(roomId, rules, locked.row().version(), user.value(), locked.now())) {
      // The row is locked by this transaction, so this cannot happen; refuse rather than guess.
      throw new RuleSetConflictException(
          RuleSetConflictException.STALE_VERSION, "The rules changed; reload and try again");
    }
    log.append(
        new RuleLogEntry(
            0,
            roomId,
            kind,
            user.value(),
            locked.now(),
            reason,
            attested,
            before,
            after,
            RuleDiffs.between(before, after)));
    scoring.recomputeRoom(roomId);
    RuleSetRow saved = sets.find(roomId).orElseThrow();
    Optional<Instant> freezesAt = freeze.freezesAt(roomId);
    return view(
        locked.inputs(),
        saved,
        FreezePolicy.frozen(saved.frozenAt(), freezesAt, locked.now()),
        freezesAt);
  }

  private List<Violation> checkRules(RuleSetDto rules, ScoringInputsDto inputs) {
    List<Violation> violations = new ArrayList<>(RuleSetValidator.validate(rules, inputs));
    if (violations.isEmpty() && json.write(rules).length() > StoredJson.RULES_MAX_CHARS) {
      violations.add(
          new Violation(
              "rules",
              "The rule set is too large; it must stay under "
                  + StoredJson.RULES_MAX_CHARS / 1024
                  + " KB once saved"));
    }
    return violations;
  }

  private static void failOn(List<Violation> violations) {
    if (!violations.isEmpty()) {
      throw new InvalidRuleSetException(violations);
    }
  }

  private static RuleSetConflictException frozen() {
    return new RuleSetConflictException(
        RuleSetConflictException.FROZEN,
        "The rules froze when the first match started; use a post-freeze change");
  }

  private static RuleSetDto effective(RuleSetRow row, ScoringInputsDto inputs) {
    return row == null || row.rules() == null ? inputs.defaultRules() : row.rules();
  }

  private static RuleSetViewDto view(
      ScoringInputsDto inputs, RuleSetRow row, boolean frozen, Optional<Instant> freezesAt) {
    boolean custom = row != null && row.rules() != null;
    return new RuleSetViewDto(
        effective(row, inputs),
        custom ? RuleSetViewDto.CUSTOM : RuleSetViewDto.PACK_DEFAULT,
        row == null ? 0 : row.version(),
        inputs.packVersion(),
        frozen,
        freezesAt.orElse(null));
  }
}
