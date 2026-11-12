package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.rooms.AbstractRoomsIT;
import com.paddleup.rooms.dto.ScoringPlayerDto;
import com.paddleup.rooms.service.ScoringRoomPort;
import com.paddleup.scoring.dto.PlayerPointsDto;
import com.paddleup.scoring.dto.ProviderStatLineDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** AC-8, AC-9, AC-16: recompute is idempotent and a manual value beats the provider's. */
class ScoringServiceIT extends AbstractRoomsIT {

  static final Instant START = Instant.parse("2027-04-01T14:00:00Z");

  @Autowired MatchStatsService matches;
  @Autowired ScoringService scoring;
  @Autowired ScoringRoomPort port;

  UUID roomA;
  UUID roomB;
  UUID datasetId;
  ScoringPlayerDto playerA;
  ScoringPlayerDto playerB;

  @BeforeEach
  void rooms() {
    importSeason("cricket-t20", SEASON_CSV);
    roomA = UUID.fromString(createRoom(person()));
    roomB = UUID.fromString(createRoom(person()));
    datasetId = port.ruleInputs(roomA).datasetId();
    assertThat(port.ruleInputs(roomB).datasetId()).isEqualTo(datasetId);
    // The same dataset player, as each room's own pool row.
    playerA = port.players(roomA).get(0);
    playerB =
        port.players(roomB).stream()
            .filter(p -> p.sourcePlayerId().equals(playerA.sourcePlayerId()))
            .findFirst()
            .orElseThrow();
    matches.upsertMatch(datasetId, "m1", START);
  }

  private void provider(String runs) {
    matches.putProviderStats(
        datasetId,
        "m1",
        List.of(
            new ProviderStatLineDto(
                playerA.sourcePlayerId(),
                Map.of("runs", new BigDecimal(runs), "in-playing-xi", BigDecimal.ONE),
                null)));
  }

  private PlayerPointsDto only(UUID room) {
    var points = scoring.points(room, "m1");
    assertThat(points).hasSize(1);
    return points.get(0);
  }

  private int rows(UUID room) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM scoring_points WHERE room_id = ? AND match_id = 'm1'",
        Integer.class,
        room);
  }

  @Test
  void recomputingTwiceLeavesOneRowPerKeyWithTheSameTotal() {
    provider("30");
    var first = only(roomA);

    scoring.recompute(roomA, "m1");
    scoring.recompute(roomA, "m1");
    scoring.recomputeRoom(roomA);
    scoring.recomputeRoom(roomA);

    assertThat(rows(roomA)).isEqualTo(1);
    var again = only(roomA);
    assertThat(again.total()).isEqualByComparingTo("34");
    assertThat(again.total()).isEqualByComparingTo(first.total());
    assertThat(again.poolPlayerId()).isEqualTo(playerA.poolPlayerId());
    assertThat(again.lines())
        .extracting("ruleId")
        .containsExactly("runs-points", "appearance-bonus");
    assertThat(again.ruleSetVersion()).isZero();
  }

  @Test
  void aReimportThatDropsAPlayerRemovesTheirStoredRow() {
    provider("30");
    assertThat(rows(roomA)).isEqualTo(1);

    matches.putProviderStats(datasetId, "m1", List.of());

    assertThat(rows(roomA)).isZero();
    assertThat(scoring.points(roomA, "m1")).isEmpty();
  }

  @Test
  void aProviderImportRecomputesEveryRoomOnTheDataset() {
    provider("30");

    assertThat(only(roomA).total()).isEqualByComparingTo("34");
    assertThat(only(roomB).total()).isEqualByComparingTo("34");
    assertThat(only(roomB).poolPlayerId()).isEqualTo(playerB.poolPlayerId());
  }

  @Test
  void aManualValueInOneRoomBeatsTheProviderAndStillDoesAfterAReimport() {
    provider("30");

    matches.putOverride(roomA, "m1", playerA.poolPlayerId(), "runs", null, new BigDecimal("50"));

    // 50 runs + 10 for the half-century + 4 for appearing; the provider's 30 is ignored.
    assertThat(only(roomA).total()).isEqualByComparingTo("64");
    assertThat(only(roomB).total()).isEqualByComparingTo("34");

    provider("40");

    assertThat(only(roomA).total()).isEqualByComparingTo("64");
    assertThat(only(roomB).total()).isEqualByComparingTo("44");
    scoring.recomputeRoom(roomA);
    assertThat(only(roomA).total()).isEqualByComparingTo("64");
  }

  @Test
  void aPlayerAddedByHandIsScoredOnManualValuesOnly() {
    UUID added = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO room_pool_players (id, room_id, source_player_id, origin, name, country,"
            + " role_key, tag_keys, base_price, set_name, provider_player_id, position)"
            + " VALUES (?, ?, NULL, 'added', 'Hand Added', 'Chile', 'BAT', '{}', 20, 'Marquee',"
            + " NULL, 99)",
        added,
        roomA);
    provider("30");

    matches.putOverride(roomA, "m1", added, "runs", null, new BigDecimal("7"));

    var byPlayer =
        scoring.points(roomA, "m1").stream()
            .collect(java.util.stream.Collectors.toMap(PlayerPointsDto::poolPlayerId, p -> p));
    assertThat(byPlayer).containsOnlyKeys(playerA.poolPlayerId(), added);
    assertThat(byPlayer.get(added).total()).isEqualByComparingTo("7");
  }
}
