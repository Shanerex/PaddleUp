package com.paddleup.scoring.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.auction.service.AuctionResultsPort;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.rooms.dto.ScoringFranchiseDto;
import com.paddleup.rooms.dto.ScoringInputsDto;
import com.paddleup.rooms.dto.ScoringPlayerDto;
import com.paddleup.rooms.service.ScoringRoomPort;
import com.paddleup.scoring.dto.ClearFieldsRequest;
import com.paddleup.scoring.dto.EvaluationDto;
import com.paddleup.scoring.dto.FieldRefDto;
import com.paddleup.scoring.dto.FieldValueDto;
import com.paddleup.scoring.dto.FranchiseDeltaDto;
import com.paddleup.scoring.dto.MatchPlayersDto;
import com.paddleup.scoring.dto.OverrideLogEntryDto;
import com.paddleup.scoring.dto.OwnerDto;
import com.paddleup.scoring.dto.SaveSheetRequest;
import com.paddleup.scoring.dto.SheetFieldDto;
import com.paddleup.scoring.dto.SheetFieldErrorDto;
import com.paddleup.scoring.dto.SheetResultDto;
import com.paddleup.scoring.dto.StatMatchDto;
import com.paddleup.scoring.dto.StatMatchPlayerDto;
import com.paddleup.scoring.dto.StatSheetViewDto;
import com.paddleup.scoring.exception.InvalidRequestException;
import com.paddleup.scoring.exception.InvalidStatSheetException;
import com.paddleup.scoring.exception.MatchRefusedException;
import com.paddleup.scoring.model.FieldKey;
import com.paddleup.scoring.model.MatchSummary;
import com.paddleup.scoring.model.OverrideLogEntry;
import com.paddleup.scoring.model.OverrideValue;
import com.paddleup.scoring.model.PointsRow;
import com.paddleup.scoring.model.ProviderValue;
import com.paddleup.scoring.repository.MatchStatsRepository;
import com.paddleup.scoring.repository.OverrideLogRepository;
import com.paddleup.scoring.repository.PointsRepository;
import com.paddleup.scoring.util.SheetFields;
import com.paddleup.scoring.util.StatValues;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual stat entry for the Commissioner. A save upserts the named fields of one player and match
 * and a clear removes them, each field on its own: fields a request does not name keep their value.
 * Every write takes the room's rule row lock first, then reads the player's overrides, validates
 * all named fields (nothing is written if one is bad), writes the overrides and one log row per
 * changed field, and recomputes the match in the same transaction. A manual value beats the
 * provider value in {@link EffectiveStats}, so it survives every re-ingest and recompute.
 */
@Service
public class ManualStatsService {

  private static final int LOG_LIMIT_MAX = 200;

  private final ScoringRoomPort roomPort;
  private final AuctionResultsPort auction;
  private final ScoringService scoring;
  private final MatchStatsRepository stats;
  private final PointsRepository points;
  private final OverrideLogRepository log;
  private final Clock clock;

  ManualStatsService(
      ScoringRoomPort roomPort,
      AuctionResultsPort auction,
      ScoringService scoring,
      MatchStatsRepository stats,
      PointsRepository points,
      OverrideLogRepository log,
      Clock clock) {
    this.roomPort = roomPort;
    this.auction = auction;
    this.scoring = scoring;
    this.stats = stats;
    this.points = points;
    this.log = log;
    this.clock = clock;
  }

  // ---- writes ----

  /** Saves the filled fields of one player's sheet; see the class comment. */
  @Transactional
  public SheetResultDto save(
      UUID roomId, UserId actor, String matchId, UUID poolPlayerId, SaveSheetRequest request) {
    roomPort.requireCommissioner(roomId, actor);
    ScoringInputsDto inputs = roomPort.ruleInputs(roomId);
    requireWithinSheet(inputs, request.fields().size());
    scoring.lockRoom(roomId);
    MatchSummary match = startedMatch(roomId, inputs, matchId);
    ScoringPlayerDto player = poolPlayer(roomId, poolPlayerId);

    Map<FieldKey, BigDecimal> wanted = validated(inputs, request.fields());
    Map<FieldKey, BigDecimal> current = overrideMap(roomId, matchId, poolPlayerId);
    BigDecimal before = storedTotal(roomId, matchId, poolPlayerId);
    Instant now = clock.instant();

    int changed = 0;
    for (var field : wanted.entrySet()) {
      BigDecimal old = current.get(field.getKey());
      if (old != null && old.compareTo(field.getValue()) == 0) {
        continue;
      }
      FieldKey key = field.getKey();
      stats.upsertOverride(
          roomId,
          matchId,
          new OverrideValue(poolPlayerId, key.stat(), key.eventType(), field.getValue()));
      log.append(
          new OverrideLogEntry(
              0,
              roomId,
              actor.value(),
              now,
              matchId,
              poolPlayerId,
              key.stat(),
              key.eventType(),
              old,
              field.getValue()));
      changed++;
    }
    return finish(roomId, actor, inputs, match, player, before, changed);
  }

  /** Removes the manual value of each named field; a field with none is left alone. */
  @Transactional
  public SheetResultDto clear(
      UUID roomId, UserId actor, String matchId, UUID poolPlayerId, ClearFieldsRequest request) {
    roomPort.requireCommissioner(roomId, actor);
    ScoringInputsDto inputs = roomPort.ruleInputs(roomId);
    requireWithinSheet(inputs, request.fields().size());
    scoring.lockRoom(roomId);
    MatchSummary match = startedMatch(roomId, inputs, matchId);
    ScoringPlayerDto player = poolPlayer(roomId, poolPlayerId);
    if (request.fields().isEmpty()) {
      throw new InvalidRequestException("Name at least one field to clear");
    }

    Set<FieldKey> named = new java.util.LinkedHashSet<>();
    List<SheetFieldErrorDto> errors = new ArrayList<>();
    for (FieldRefDto ref : request.fields()) {
      FieldKey key = key(ref.stat(), ref.eventType());
      String problem = nameProblem(inputs, key);
      if (problem != null) {
        errors.add(error(inputs, key, problem));
      } else {
        named.add(key);
      }
    }
    if (!errors.isEmpty()) {
      throw refused(errors);
    }

    Map<FieldKey, BigDecimal> current = overrideMap(roomId, matchId, poolPlayerId);
    BigDecimal before = storedTotal(roomId, matchId, poolPlayerId);
    Instant now = clock.instant();
    List<FieldKey> removed = new ArrayList<>();
    for (FieldKey key : named) {
      BigDecimal old = current.get(key);
      if (old == null) {
        continue;
      }
      removed.add(key);
      log.append(
          new OverrideLogEntry(
              0,
              roomId,
              actor.value(),
              now,
              matchId,
              poolPlayerId,
              key.stat(),
              key.eventType(),
              old,
              null));
    }
    stats.deleteOverrides(roomId, matchId, poolPlayerId, removed);
    return finish(roomId, actor, inputs, match, player, before, removed.size());
  }

  private SheetResultDto finish(
      UUID roomId,
      UserId actor,
      ScoringInputsDto inputs,
      MatchSummary match,
      ScoringPlayerDto player,
      BigDecimal before,
      int changed) {
    String matchId = match.matchId();
    if (changed > 0) {
      scoring.recompute(roomId, matchId);
    }
    BigDecimal after = storedTotal(roomId, matchId, player.poolPlayerId());
    EvaluationDto providerOnly = scoring.evaluate(roomId, matchId, player.poolPlayerId(), false);
    UUID owner = auction.owners(roomId).get(player.poolPlayerId());
    FranchiseDeltaDto franchise =
        owner == null ? null : new FranchiseDeltaDto(owner, zero(after).subtract(zero(before)));
    StatSheetViewDto sheet = sheetView(roomId, inputs, match, player);
    return new SheetResultDto(
        before,
        after,
        providerOnly == null ? null : providerOnly.total(),
        franchise,
        changed,
        sheet);
  }

  // ---- reads ----

  /** The room's dataset matches in play order with their source chip. */
  @Transactional(readOnly = true)
  public List<StatMatchDto> matches(UUID roomId, UserId user) {
    roomPort.requireCommissioner(roomId, user);
    ScoringInputsDto inputs = roomPort.ruleInputs(roomId);
    return matchDtos(roomId, inputs, clock.instant());
  }

  /** The whole pool for one match, players with a value first. */
  @Transactional(readOnly = true)
  public MatchPlayersDto matchPlayers(UUID roomId, UserId user, String matchId) {
    roomPort.requireCommissioner(roomId, user);
    ScoringInputsDto inputs = roomPort.ruleInputs(roomId);
    MatchSummary summary = summary(roomId, inputs, matchId);
    StatMatchDto match = matchDto(summary, rank(roomId, inputs, matchId), clock.instant());
    requireStarted(match);

    List<ScoringPlayerDto> pool = roomPort.players(roomId);
    Map<UUID, OwnerDto> owners = owners(roomId);
    Map<UUID, BigDecimal> totals = new HashMap<>();
    points.forMatch(roomId, matchId).forEach(r -> totals.put(r.poolPlayerId(), r.total()));
    Set<UUID> withProvider = new HashSet<>();
    for (ProviderValue v : stats.providerValues(inputs.datasetId(), matchId)) {
      withProvider.add(v.seasonPlayerId());
    }
    Set<UUID> withManual = new HashSet<>();
    stats.overrides(roomId, matchId).forEach(o -> withManual.add(o.poolPlayerId()));

    List<StatMatchPlayerDto> valued = new ArrayList<>();
    List<StatMatchPlayerDto> rest = new ArrayList<>();
    for (ScoringPlayerDto p : pool) {
      boolean provider = p.sourcePlayerId() != null && withProvider.contains(p.sourcePlayerId());
      boolean manual = withManual.contains(p.poolPlayerId());
      String chip =
          !provider && !manual
              ? null
              : provider && manual ? "PROVIDER_MANUAL" : manual ? "MANUAL" : "PROVIDER";
      var row =
          new StatMatchPlayerDto(
              p.poolPlayerId(),
              p.name(),
              p.role(),
              owners.get(p.poolPlayerId()),
              chip,
              totals.get(p.poolPlayerId()));
      (chip == null ? rest : valued).add(row);
    }
    int withValues = valued.size();
    valued.addAll(rest);
    return new MatchPlayersDto(match, withValues, pool.size(), valued);
  }

  /** One player's sheet with the provider and manual value of every field. */
  @Transactional(readOnly = true)
  public StatSheetViewDto sheet(UUID roomId, UserId user, String matchId, UUID poolPlayerId) {
    roomPort.requireCommissioner(roomId, user);
    ScoringInputsDto inputs = roomPort.ruleInputs(roomId);
    MatchSummary match = startedMatch(roomId, inputs, matchId);
    return sheetView(roomId, inputs, match, poolPlayer(roomId, poolPlayerId));
  }

  /** The room's override log after {@code after}, oldest first; any member reads. */
  @Transactional(readOnly = true)
  public List<OverrideLogEntryDto> overrideLog(UUID roomId, UserId user, long after, int limit) {
    roomPort.access(roomId, user);
    int size = Math.max(1, Math.min(limit, LOG_LIMIT_MAX));
    return log.page(roomId, Math.max(0, after), size).stream()
        .map(
            e ->
                new OverrideLogEntryDto(
                    e.seq(),
                    e.actor(),
                    e.occurredAt(),
                    e.matchId(),
                    e.poolPlayerId(),
                    e.stat(),
                    e.eventType(),
                    e.oldValue(),
                    e.newValue()))
        .toList();
  }

  // ---- helpers ----

  /**
   * A request names at most as many fields as the sheet declares; more is refused before any work.
   */
  private static void requireWithinSheet(ScoringInputsDto inputs, int named) {
    if (named > SheetFields.of(inputs.stats()).size()) {
      throw new InvalidRequestException("More fields than the sheet has");
    }
  }

  private StatSheetViewDto sheetView(
      UUID roomId, ScoringInputsDto inputs, MatchSummary summary, ScoringPlayerDto player) {
    String matchId = summary.matchId();
    Map<FieldKey, BigDecimal> manual = overrideMap(roomId, matchId, player.poolPlayerId());
    Map<FieldKey, BigDecimal> provider = new HashMap<>();
    if (inputs.datasetId() != null && player.sourcePlayerId() != null) {
      for (ProviderValue v : stats.providerValues(inputs.datasetId(), matchId)) {
        if (v.seasonPlayerId().equals(player.sourcePlayerId())) {
          provider.put(new FieldKey(v.stat(), v.eventType()), v.value());
        }
      }
    }
    List<SheetFieldDto> fields = new ArrayList<>();
    for (SheetFieldDto f : SheetFields.of(inputs.stats())) {
      FieldKey key = new FieldKey(f.stat(), f.eventType());
      fields.add(f.withValues(provider.get(key), manual.get(key)));
    }
    EvaluationDto providerOnly = scoring.evaluate(roomId, matchId, player.poolPlayerId(), false);
    return new StatSheetViewDto(
        matchDto(summary, rank(roomId, inputs, matchId), clock.instant()),
        player.poolPlayerId(),
        player.name(),
        player.role(),
        owners(roomId).get(player.poolPlayerId()),
        storedTotal(roomId, matchId, player.poolPlayerId()),
        providerOnly == null ? null : providerOnly.total(),
        fields);
  }

  /** The match, which must exist in the room's dataset and have started by the server clock. */
  private MatchSummary startedMatch(UUID roomId, ScoringInputsDto inputs, String matchId) {
    MatchSummary summary = summary(roomId, inputs, matchId);
    if (summary.startsAt().isAfter(clock.instant())) {
      throw MatchRefusedException.notStarted();
    }
    return summary;
  }

  private void requireStarted(StatMatchDto match) {
    if ("UPCOMING".equals(match.chip())) {
      throw MatchRefusedException.notStarted();
    }
  }

  private MatchSummary summary(UUID roomId, ScoringInputsDto inputs, String matchId) {
    if (inputs.datasetId() == null || matchId == null) {
      throw MatchRefusedException.unknownMatch();
    }
    return stats.matchSummaries(roomId, inputs.datasetId()).stream()
        .filter(m -> m.matchId().equals(matchId))
        .findFirst()
        .orElseThrow(MatchRefusedException::unknownMatch);
  }

  private int rank(UUID roomId, ScoringInputsDto inputs, String matchId) {
    List<MatchSummary> all = stats.matchSummaries(roomId, inputs.datasetId());
    for (int i = 0; i < all.size(); i++) {
      if (all.get(i).matchId().equals(matchId)) {
        return i + 1;
      }
    }
    return 0;
  }

  private List<StatMatchDto> matchDtos(UUID roomId, ScoringInputsDto inputs, Instant now) {
    if (inputs.datasetId() == null) {
      return List.of();
    }
    List<MatchSummary> all = stats.matchSummaries(roomId, inputs.datasetId());
    List<StatMatchDto> out = new ArrayList<>();
    for (int i = 0; i < all.size(); i++) {
      out.add(matchDto(all.get(i), i + 1, now));
    }
    return out;
  }

  private static StatMatchDto matchDto(MatchSummary m, int n, Instant now) {
    return new StatMatchDto(
        m.matchId(), n, m.label(), m.startsAt(), MatchChips.of(m, now), m.overridePlayers());
  }

  private ScoringPlayerDto poolPlayer(UUID roomId, UUID poolPlayerId) {
    return roomPort.players(roomId).stream()
        .filter(p -> p.poolPlayerId().equals(poolPlayerId))
        .findFirst()
        .orElseThrow(MatchRefusedException::unknownPlayer);
  }

  private Map<FieldKey, BigDecimal> overrideMap(UUID roomId, String matchId, UUID poolPlayerId) {
    Map<FieldKey, BigDecimal> map = new HashMap<>();
    for (OverrideValue v : stats.overridesFor(roomId, matchId, poolPlayerId)) {
      map.put(new FieldKey(v.stat(), v.eventType()), v.value());
    }
    return map;
  }

  private BigDecimal storedTotal(UUID roomId, String matchId, UUID poolPlayerId) {
    return points.find(roomId, matchId, poolPlayerId).map(PointsRow::total).orElse(null);
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private Map<UUID, OwnerDto> owners(UUID roomId) {
    Map<UUID, UUID> byPlayer = auction.owners(roomId);
    if (byPlayer.isEmpty()) {
      return Map.of();
    }
    Map<UUID, ScoringFranchiseDto> teams = new HashMap<>();
    roomPort.franchises(roomId).forEach(f -> teams.put(f.id(), f));
    Map<UUID, OwnerDto> out = new HashMap<>();
    byPlayer.forEach(
        (player, team) -> {
          ScoringFranchiseDto f = teams.get(team);
          if (f != null) {
            out.put(player, new OwnerDto(f.id(), f.name(), f.shortName(), f.colour()));
          }
        });
    return out;
  }

  private static FieldKey key(String stat, String eventType) {
    return new FieldKey(stat, eventType == null ? EffectiveStats.NO_EVENT : eventType);
  }

  /** Null when the key names a known stat (and a declared event type where it has them). */
  private static String nameProblem(ScoringInputsDto inputs, FieldKey key) {
    StatDto stat = declared(inputs, key.stat());
    if (stat == null) {
      return "unknown_stat";
    }
    boolean typed = !stat.eventTypes().isEmpty();
    if (typed ? !stat.eventTypes().contains(key.eventType()) : !key.eventType().isEmpty()) {
      return "unknown_event_type";
    }
    return null;
  }

  private static StatDto declared(ScoringInputsDto inputs, String stat) {
    return inputs.stats().stream().filter(s -> s.id().equals(stat)).findFirst().orElse(null);
  }

  private static SheetFieldErrorDto error(ScoringInputsDto inputs, FieldKey key, String reason) {
    StatDto stat = declared(inputs, key.stat());
    return new SheetFieldErrorDto(
        key.stat(),
        key.eventType(),
        stat == null ? null : StatValues.min(stat),
        stat == null ? null : StatValues.max(stat),
        reason);
  }

  /**
   * Checks every filled field and returns its normalised value by key. A blank (null) value is
   * skipped. Any bad field refuses the whole sheet naming each one; a sheet with no filled field is
   * refused as empty.
   */
  private static Map<FieldKey, BigDecimal> validated(
      ScoringInputsDto inputs, List<FieldValueDto> fields) {
    Map<FieldKey, BigDecimal> values = new LinkedHashMap<>();
    List<SheetFieldErrorDto> errors = new ArrayList<>();
    for (FieldValueDto f : fields) {
      if (f.value() == null) {
        continue;
      }
      FieldKey key = key(f.stat(), f.eventType());
      String problem = nameProblem(inputs, key);
      if (problem == null && values.containsKey(key)) {
        problem = "duplicate_field";
      }
      if (problem == null) {
        var check = StatValues.check(declared(inputs, key.stat()), f.value());
        if (check.valid()) {
          values.put(key, check.value());
          continue;
        }
        problem = check.reason();
      }
      errors.add(error(inputs, key, problem));
    }
    if (!errors.isEmpty()) {
      throw refused(errors);
    }
    if (values.isEmpty()) {
      throw new InvalidStatSheetException(
          InvalidStatSheetException.EMPTY,
          "Nothing to save. To remove a manual value, use clear",
          List.of());
    }
    return values;
  }

  private static InvalidStatSheetException refused(List<SheetFieldErrorDto> errors) {
    String message =
        errors.size() == 1
            ? "1 field needs fixing. Nothing was saved."
            : errors.size() + " fields need fixing. Nothing was saved.";
    return new InvalidStatSheetException(InvalidStatSheetException.INVALID, message, errors);
  }
}
