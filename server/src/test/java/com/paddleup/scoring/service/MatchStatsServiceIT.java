package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.rooms.AbstractRoomsIT;
import com.paddleup.rooms.service.ScoringRoomPort;
import com.paddleup.scoring.dto.ProviderStatLineDto;
import com.paddleup.scoring.model.OverrideValue;
import com.paddleup.scoring.repository.MatchStatsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** AC-9: fixtures and provider stats are per dataset; manual values are per room and sparse. */
class MatchStatsServiceIT extends AbstractRoomsIT {

  static final Instant START = Instant.parse("2027-04-01T14:00:00Z");

  @Autowired MatchStatsService service;
  @Autowired MatchStatsRepository stats;
  @Autowired ScoringRoomPort port;

  UUID roomId;
  UUID datasetId;
  UUID poolPlayer;
  UUID seasonPlayer;

  @BeforeEach
  void room() {
    importSeason("cricket-t20", SEASON_CSV);
    Person commissioner = person();
    roomId = UUID.fromString(createRoom(commissioner));
    datasetId = port.ruleInputs(roomId).datasetId();
    var first = port.players(roomId).get(0);
    poolPlayer = first.poolPlayerId();
    seasonPlayer = first.sourcePlayerId();
  }

  @Test
  void upsertingAMatchTwiceKeepsOneRowAndMovesItsStart() {
    service.upsertMatch(datasetId, "m1", START);
    service.upsertMatch(datasetId, "m1", START.plusSeconds(3600));

    assertThat(stats.earliestStart(datasetId)).contains(START.plusSeconds(3600));
    service.upsertMatch(datasetId, "m0", START.minusSeconds(60));
    assertThat(stats.earliestStart(datasetId)).contains(START.minusSeconds(60));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM scoring_matches WHERE dataset_id = ?",
                Integer.class,
                datasetId))
        .isEqualTo(2);
  }

  @Test
  void aDatasetWithNoMatchesHasNoEarliestStart() {
    assertThat(stats.earliestStart(UUID.randomUUID())).isEmpty();
    assertThat(stats.earliestStart(datasetId)).isEmpty();
  }

  @Test
  void providerStatsAreStoredOncePerDatasetAndAReplaceDropsTheOldRows() {
    service.upsertMatch(datasetId, "m1", START);
    String second = createRoom(person());
    var line =
        new ProviderStatLineDto(
            seasonPlayer,
            Map.of("runs", new BigDecimal("54"), "balls-faced", new BigDecimal("30")),
            Map.of("wickets", Map.of("lbw", new BigDecimal("1"))));

    service.putProviderStats(datasetId, "m1", List.of(line));

    assertThat(stats.providerValues(datasetId, "m1")).hasSize(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM scoring_stat_values WHERE dataset_id = ?",
                Integer.class,
                datasetId))
        .isEqualTo(3);
    assertThat(second).isNotBlank();

    service.putProviderStats(
        datasetId,
        "m1",
        List.of(new ProviderStatLineDto(seasonPlayer, Map.of("runs", BigDecimal.TEN), null)));

    var after = stats.providerValues(datasetId, "m1");
    assertThat(after).hasSize(1);
    assertThat(after.get(0).stat()).isEqualTo("runs");
    assertThat(after.get(0).eventType()).isEmpty();
    assertThat(after.get(0).value()).isEqualByComparingTo("10");
  }

  @Test
  void providerStatsForAnUnknownMatchAreRefused() {
    assertThatThrownBy(() -> service.putProviderStats(datasetId, "nope", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anOverrideIsStoredForOneRoomOnlyAndAnEarlierValueIsReplaced() {
    service.upsertMatch(datasetId, "m1", START);
    String other = createRoom(person());

    service.putOverride(roomId, "m1", poolPlayer, "runs", null, new BigDecimal("40"));
    service.putOverride(roomId, "m1", poolPlayer, "runs", "", new BigDecimal("41"));
    service.putOverride(roomId, "m1", poolPlayer, "wickets", "lbw", new BigDecimal("1"));

    var mine = stats.overrides(roomId, "m1");
    assertThat(mine)
        .extracting(OverrideValue::stat, OverrideValue::eventType)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("runs", ""),
            org.assertj.core.groups.Tuple.tuple("wickets", "lbw"));
    assertThat(mine.stream().filter(o -> o.stat().equals("runs")).findFirst().get().value())
        .isEqualByComparingTo("41");
    assertThat(stats.overrides(UUID.fromString(other), "m1")).isEmpty();
  }

  @Test
  void anOverrideForAnUnknownThingIsRefusedAndNothingIsSaved() {
    service.upsertMatch(datasetId, "m1", START);
    var v = new BigDecimal("1");

    assertThatThrownBy(() -> service.putOverride(roomId, "zz", poolPlayer, "runs", null, v))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.putOverride(roomId, "m1", UUID.randomUUID(), "runs", null, v))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.putOverride(roomId, "m1", poolPlayer, "nope", null, v))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.putOverride(roomId, "m1", poolPlayer, "wickets", "zz", v))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.putOverride(roomId, "m1", poolPlayer, "runs", null, new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.putOverride(roomId, "m1", poolPlayer, "runs", null, null))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(stats.overrides(roomId, "m1")).isEmpty();
  }

  @Test
  void scoredMatchesListsMatchesWithProviderRowsOrOverrides() {
    service.upsertMatch(datasetId, "m1", START);
    service.upsertMatch(datasetId, "m2", START);
    service.upsertMatch(datasetId, "m3", START);
    service.putProviderStats(
        datasetId,
        "m1",
        List.of(new ProviderStatLineDto(seasonPlayer, Map.of("runs", BigDecimal.ONE), null)));
    service.putOverride(roomId, "m2", poolPlayer, "runs", null, BigDecimal.ONE);

    assertThat(stats.scoredMatches(roomId, datasetId)).containsExactly("m1", "m2");
    assertThat(stats.scoredMatches(roomId, null)).containsExactly("m2");
  }
}
