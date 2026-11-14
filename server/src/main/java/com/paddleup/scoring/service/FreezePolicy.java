package com.paddleup.scoring.service;

import com.paddleup.rooms.dto.ScoringInputsDto;
import com.paddleup.scoring.model.RuleSetRow;
import com.paddleup.scoring.repository.MatchStatsRepository;
import com.paddleup.scoring.repository.RuleSetRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * When a room's rules freeze: at the scheduled start of the room's first match, the earliest start
 * at or after the room was created among the matches of the season dataset its pool was copied
 * from. Matches that started before the room existed do not count, so a room made mid-season can
 * still be customised until its next match. A room with no pool, or no such match, has no freeze
 * instant. The instant is compared with the server clock only; a client never says.
 */
@Component
class FreezePolicy {

  private final MatchStatsRepository matches;
  private final RuleSetRepository sets;

  FreezePolicy(MatchStatsRepository matches, RuleSetRepository sets) {
    this.matches = matches;
    this.sets = sets;
  }

  /** The instant the room freezes, or empty when it has no pool or no match yet. */
  Optional<Instant> freezesAt(ScoringInputsDto inputs) {
    UUID dataset = inputs.datasetId();
    if (dataset == null) {
      return Optional.empty();
    }
    return inputs.roomCreatedAt() == null
        ? matches.earliestStart(dataset)
        : matches.earliestStart(dataset, inputs.roomCreatedAt());
  }

  /**
   * Whether the room is frozen at {@code now}, recording the freeze on its rule row the first time
   * it is seen, so a start later moved out of reach cannot thaw it. The caller holds the row lock.
   */
  boolean recordIfDue(UUID roomId, RuleSetRow row, ScoringInputsDto inputs, Instant now) {
    if (row.frozenAt() != null) {
      return true;
    }
    Optional<Instant> at = freezesAt(inputs);
    if (!frozen(null, at, now)) {
      return false;
    }
    sets.markFrozen(roomId, at.orElseThrow());
    return true;
  }

  /**
   * True once the freeze has been recorded, or the clock is at or past the freeze instant. A start
   * that is later moved out of reach does not thaw a room that has been seen frozen.
   */
  static boolean frozen(Instant recordedAt, Optional<Instant> freezesAt, Instant now) {
    return recordedAt != null || freezesAt.map(at -> !now.isBefore(at)).orElse(false);
  }

  /** The instant to report: the recorded freeze when there is one, else the scheduled one. */
  static Optional<Instant> reported(Instant recordedAt, Optional<Instant> freezesAt) {
    return recordedAt != null ? Optional.of(recordedAt) : freezesAt;
  }
}
