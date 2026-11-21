package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.scoring.ScoringIT;
import com.paddleup.scoring.dto.FieldValueDto;
import com.paddleup.scoring.dto.ProviderStatLineDto;
import com.paddleup.scoring.dto.SaveSheetRequest;
import com.paddleup.scoring.model.StatLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** AC-3, AC-4, AC-5: the state x entry-point matrix, manual values survive every other entry. */
class ManualStatsMatrixIT extends ScoringIT {

  @Autowired ManualStatsService manual;

  @BeforeEach
  void started() {
    clock.freeze(START.plusSeconds(60));
  }

  private void save(String stat, String value) {
    manual.save(
        roomId,
        user(commissioner),
        "m1",
        player.poolPlayerId(),
        new SaveSheetRequest(List.of(new FieldValueDto(stat, null, new BigDecimal(value)))));
  }

  private void ingest(Map<String, BigDecimal> stats) {
    matches.putProviderStats(
        datasetId, "m1", List.of(new ProviderStatLineDto(player.sourcePlayerId(), stats, null)));
  }

  private BigDecimal stored() {
    return scoring.points(roomId, "m1").get(0).total();
  }

  private BigDecimal evaluated(Map<String, BigDecimal> stats) {
    var inputs = port.ruleInputs(roomId);
    return PointsEvaluator.evaluate(
            inputs.defaultRules(),
            inputs.appearanceRule(),
            inputs.stats(),
            player.role(),
            new StatLine(stats, Map.of()))
        .total();
  }

  private int rows(String table) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE room_id = ?", Integer.class, roomId);
  }

  @Test
  void reIngestAfterASaveKeepsTheManualFieldAndUpdatesOnlyFieldsWithoutAnOverride() {
    ingest(Map.of("runs", new BigDecimal("46"), "in-playing-xi", BigDecimal.ONE));
    save("runs", "52");
    BigDecimal afterSave = stored();

    ingest(
        Map.of(
            "runs", new BigDecimal("70"),
            "in-playing-xi", BigDecimal.ONE,
            "fours", new BigDecimal("5")));

    assertThat(stored())
        .isNotEqualByComparingTo(afterSave)
        .isEqualByComparingTo(
            evaluated(
                Map.of(
                    "runs", new BigDecimal("52"),
                    "in-playing-xi", BigDecimal.ONE,
                    "fours", new BigDecimal("5"))));
    assertThat(
            jdbc.queryForObject(
                "SELECT value FROM scoring_stat_overrides WHERE room_id = ? AND stat = 'runs'",
                BigDecimal.class,
                roomId))
        .isEqualByComparingTo("52");
  }

  @Test
  void aDoubleSaveAndADoubleIngestLeaveIdenticalTotalsOneRowAndOneLogRowPerChangedField() {
    ingest(Map.of("runs", new BigDecimal("46"), "in-playing-xi", BigDecimal.ONE));
    save("runs", "52");
    BigDecimal first = stored();
    save("runs", "52");
    ingest(Map.of("runs", new BigDecimal("46"), "in-playing-xi", BigDecimal.ONE));
    ingest(Map.of("runs", new BigDecimal("46"), "in-playing-xi", BigDecimal.ONE));

    assertThat(stored()).isEqualByComparingTo(first);
    assertThat(rows("scoring_points")).isEqualTo(1);
    assertThat(rows("scoring_override_log")).isEqualTo(1);
    assertThat(rows("scoring_stat_overrides")).isEqualTo(1);
  }

  @Test
  void aRuleEditRecomputeKeepsEveryOverrideInEffect() {
    ingest(Map.of("runs", new BigDecimal("46"), "in-playing-xi", BigDecimal.ONE));
    save("runs", "52");
    BigDecimal before = stored();

    scoring.recomputeRoom(roomId);

    assertThat(stored()).isEqualByComparingTo(before);
    assertThat(rows("scoring_stat_overrides")).isEqualTo(1);
  }

  @Test
  void aManualOnlyPlayerHasNoProviderDataAndAnIngestForOthersDoesNotTouchIt() {
    var other = port.players(roomId).get(1);
    manual.save(
        roomId,
        user(commissioner),
        "m1",
        other.poolPlayerId(),
        new SaveSheetRequest(List.of(new FieldValueDto("runs", null, new BigDecimal("10")))));
    BigDecimal manualOnly =
        scoring.points(roomId, "m1").stream()
            .filter(p -> p.poolPlayerId().equals(other.poolPlayerId()))
            .findFirst()
            .orElseThrow()
            .total();

    ingest(Map.of("runs", new BigDecimal("46"), "in-playing-xi", BigDecimal.ONE));

    assertThat(scoring.points(roomId, "m1")).hasSize(2);
    assertThat(
            scoring.points(roomId, "m1").stream()
                .filter(p -> p.poolPlayerId().equals(other.poolPlayerId()))
                .findFirst()
                .orElseThrow()
                .total())
        .isEqualByComparingTo(manualOnly);
  }
}
