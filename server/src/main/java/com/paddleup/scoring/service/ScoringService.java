package com.paddleup.scoring.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.rooms.dto.ScoringInputsDto;
import com.paddleup.rooms.dto.ScoringPlayerDto;
import com.paddleup.rooms.service.ScoringRoomPort;
import com.paddleup.scoring.dto.EvaluationDto;
import com.paddleup.scoring.dto.MatchPointsDto;
import com.paddleup.scoring.dto.PlayerPointsDto;
import com.paddleup.scoring.model.PointsRow;
import com.paddleup.scoring.model.RuleSetRow;
import com.paddleup.scoring.model.StatLine;
import com.paddleup.scoring.repository.MatchStatsRepository;
import com.paddleup.scoring.repository.PointsRepository;
import com.paddleup.scoring.repository.RuleSetRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes and reads stored points. A recompute is idempotent: it replaces the stored rows of a
 * room and match with what the room's current rules and effective stats give, so running it twice
 * leaves one row per player with the same total.
 */
@Service
public class ScoringService {

  private final ScoringRoomPort roomPort;
  private final RuleSetRepository ruleSets;
  private final MatchStatsRepository stats;
  private final PointsRepository points;
  private final FreezePolicy freeze;
  private final Clock clock;

  ScoringService(
      ScoringRoomPort roomPort,
      RuleSetRepository ruleSets,
      MatchStatsRepository stats,
      PointsRepository points,
      FreezePolicy freeze,
      Clock clock) {
    this.roomPort = roomPort;
    this.ruleSets = ruleSets;
    this.stats = stats;
    this.points = points;
    this.freeze = freeze;
    this.clock = clock;
  }

  /** The rules the room scores by now: its edited set, else its pack-default snapshot. */
  private record Rules(RuleSetDto set, int version) {}

  /**
   * Reads the rules under the room's rule row lock, creating the row first when absent. A rule edit
   * holds the same lock until it commits, so a recompute that started during an edit waits and then
   * scores with the committed latest rules and version, never an older set. A recompute at or past
   * the freeze instant records the freeze, so a room that has been scored cannot thaw.
   */
  private Rules rulesOf(UUID roomId, ScoringInputsDto inputs) {
    ruleSets.insertIfAbsent(roomId, clock.instant());
    RuleSetRow row = ruleSets.lock(roomId).orElseThrow();
    freeze.recordIfDue(roomId, row, inputs, clock.instant());
    if (row.rules() == null) {
      return new Rules(inputs.defaultRules(), row.version());
    }
    return new Rules(row.rules(), row.version());
  }

  /** Scores every pool player that has a stat in this match, replacing the stored rows. */
  @Transactional
  public void recompute(UUID roomId, String matchId) {
    ScoringInputsDto inputs = roomPort.ruleInputs(roomId);
    recompute(roomId, matchId, inputs, rulesOf(roomId, inputs), roomPort.players(roomId));
  }

  /** Recomputes every match of the room that has any stat. */
  @Transactional
  public void recomputeRoom(UUID roomId) {
    recomputeRoom(roomId, roomPort.ruleInputs(roomId));
  }

  /** As {@link #recomputeRoom(UUID)}, with the room's inputs already read by the caller. */
  @Transactional
  void recomputeRoom(UUID roomId, ScoringInputsDto inputs) {
    Rules rules = rulesOf(roomId, inputs);
    List<ScoringPlayerDto> players = roomPort.players(roomId);
    for (String matchId : stats.scoredMatches(roomId, inputs.datasetId())) {
      recompute(roomId, matchId, inputs, rules, players);
    }
  }

  private void recompute(
      UUID roomId,
      String matchId,
      ScoringInputsDto inputs,
      Rules rules,
      List<ScoringPlayerDto> players) {
    var provider =
        inputs.datasetId() == null
            ? List.<com.paddleup.scoring.model.ProviderValue>of()
            : stats.providerValues(inputs.datasetId(), matchId);
    Map<UUID, StatLine> lines =
        EffectiveStats.forPlayers(players, provider, stats.overrides(roomId, matchId));
    Map<UUID, String> roles = new java.util.HashMap<>();
    players.forEach(p -> roles.put(p.poolPlayerId(), p.role()));
    Instant now = clock.instant();
    List<PointsRow> rows = new ArrayList<>();
    lines.forEach(
        (playerId, line) -> {
          EvaluationDto result =
              PointsEvaluator.evaluate(
                  rules.set(), inputs.appearanceRule(), inputs.stats(), roles.get(playerId), line);
          rows.add(new PointsRow(playerId, result.total(), result.lines(), rules.version(), now));
        });
    points.replace(roomId, matchId, rows);
  }

  /** The match's points for any member of the room; 404 for a non-member. */
  @Transactional(readOnly = true)
  public MatchPointsDto matchPoints(UUID roomId, UserId user, String matchId) {
    roomPort.access(roomId, user);
    return new MatchPointsDto(matchId, points(roomId, matchId));
  }

  /** The stored points of the room's match, one entry per player scored. */
  @Transactional(readOnly = true)
  public List<PlayerPointsDto> points(UUID roomId, String matchId) {
    return points.forMatch(roomId, matchId).stream()
        .map(r -> new PlayerPointsDto(r.poolPlayerId(), r.total(), r.lines(), r.ruleSetVersion()))
        .toList();
  }
}
