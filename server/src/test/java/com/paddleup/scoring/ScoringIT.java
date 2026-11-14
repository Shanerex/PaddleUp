package com.paddleup.scoring;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.rooms.AbstractRoomsIT;
import com.paddleup.rooms.dto.ScoringPlayerDto;
import com.paddleup.rooms.service.ScoringRoomPort;
import com.paddleup.scoring.dto.ProviderStatLineDto;
import com.paddleup.scoring.service.MatchStatsService;
import com.paddleup.scoring.service.ScoringService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A room on a fresh cricket dataset with one match, a Commissioner and a manager, and a clock the
 * test sets. Shared by the scoring integration tests that exercise the rule set lifecycle.
 */
public abstract class ScoringIT extends AbstractRoomsIT {

  /** The scheduled start of the one match, which is when the room's rules freeze. */
  protected static final Instant START = Instant.parse("2027-04-01T14:00:00Z");

  @Autowired protected MatchStatsService matches;
  @Autowired protected ScoringService scoring;
  @Autowired protected ScoringRoomPort port;

  protected Person commissioner;
  protected Person manager;
  protected UUID roomId;
  protected UUID datasetId;
  protected ScoringPlayerDto player;

  @BeforeEach
  void scoringRoom() {
    importSeason("cricket-t20", SEASON_CSV);
    commissioner = person();
    roomId = UUID.fromString(createRoom(commissioner));
    manager = insertManager(roomId.toString(), "sky");
    datasetId = port.ruleInputs(roomId).datasetId();
    player = port.players(roomId).get(0);
    matches.upsertMatch(datasetId, "m1", START);
    clock.freeze(START.minusSeconds(3600));
  }

  protected UserId user(Person who) {
    return new UserId(userId(who));
  }

  /** A second room on the same dataset, with its own Commissioner. */
  protected UUID otherRoom() {
    return UUID.fromString(createRoom(person()));
  }

  /** The default rules with the points per run changed, as an edited set. */
  protected RuleSetDto runsWorth(RuleSetDto base, String points) {
    return new RuleSetDto(
        base.rules().stream()
            .map(
                r ->
                    r instanceof RuleDto.PerUnit p && p.id().equals("runs-points")
                        ? (RuleDto)
                            new RuleDto.PerUnit(p.id(), p.stat(), new BigDecimal(points), p.label())
                        : r)
            .toList());
  }

  /** Provider stats for the room's first player in the given match: runs and an XI place. */
  protected void provider(UUID dataset, String matchId, UUID seasonPlayer, String runs) {
    matches.putProviderStats(
        dataset,
        matchId,
        List.of(
            new ProviderStatLineDto(
                seasonPlayer,
                Map.of("runs", new BigDecimal(runs), "in-playing-xi", BigDecimal.ONE),
                null)));
  }

  protected int version(UUID room) {
    var rows =
        jdbc.queryForList(
            "SELECT version FROM scoring_rule_sets WHERE room_id = ?", Integer.class, room);
    return rows.isEmpty() ? 0 : rows.get(0);
  }

  protected int logCount(UUID room) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM scoring_rule_log WHERE room_id = ?", Integer.class, room);
  }
}
