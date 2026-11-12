package com.paddleup.scoring.service;

import com.paddleup.rooms.service.ScoringRoomPort;
import com.paddleup.scoring.repository.MatchStatsRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * When a room's rules freeze: at the earliest scheduled start among the matches of the season
 * dataset its pool was copied from. A room with no pool, or a dataset with no matches, has no
 * freeze instant. The instant is compared with the server clock only; a client never says.
 */
@Component
class FreezePolicy {

  private final ScoringRoomPort roomPort;
  private final MatchStatsRepository matches;

  FreezePolicy(ScoringRoomPort roomPort, MatchStatsRepository matches) {
    this.roomPort = roomPort;
    this.matches = matches;
  }

  /** The instant the room freezes, or empty when it has no pool or no match yet. */
  Optional<Instant> freezesAt(UUID roomId) {
    UUID dataset = roomPort.ruleInputs(roomId).datasetId();
    return dataset == null ? Optional.empty() : matches.earliestStart(dataset);
  }

  /**
   * True once the freeze has been recorded, or the clock is at or past the freeze instant. A start
   * that is later moved out of reach does not thaw a room that has been seen frozen.
   */
  static boolean frozen(Instant recordedAt, Optional<Instant> freezesAt, Instant now) {
    return recordedAt != null || freezesAt.map(at -> !now.isBefore(at)).orElse(false);
  }
}
