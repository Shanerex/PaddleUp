package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.rooms.exception.RoomForbiddenException;
import com.paddleup.rooms.exception.RoomNotFoundException;
import com.paddleup.scoring.ScoringIT;
import com.paddleup.scoring.dto.ClearFieldsRequest;
import com.paddleup.scoring.dto.FieldRefDto;
import com.paddleup.scoring.dto.FieldValueDto;
import com.paddleup.scoring.dto.SaveSheetRequest;
import com.paddleup.scoring.dto.SheetFieldErrorDto;
import com.paddleup.scoring.dto.SheetResultDto;
import com.paddleup.scoring.exception.InvalidStatSheetException;
import com.paddleup.scoring.exception.MatchRefusedException;
import com.paddleup.scoring.model.OverrideValue;
import com.paddleup.scoring.model.StatLine;
import com.paddleup.scoring.repository.MatchStatsRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AC-2, AC-6 to AC-9, AC-11 to AC-14: a save upserts the named fields only, validates all of them
 * before writing, and a clear removes only the named ones.
 */
class ManualStatsServiceIT extends ScoringIT {

  @Autowired ManualStatsService manual;
  @Autowired MatchStatsRepository stats;

  @BeforeEach
  void started() {
    clock.freeze(START.plusSeconds(60));
  }

  // ---- helpers ----

  static FieldValueDto field(String stat, String value) {
    return new FieldValueDto(stat, null, value == null ? null : new BigDecimal(value));
  }

  static FieldValueDto typed(String stat, String type, String value) {
    return new FieldValueDto(stat, type, value == null ? null : new BigDecimal(value));
  }

  SheetResultDto save(UUID player, FieldValueDto... fields) {
    return manual.save(
        roomId, user(commissioner), "m1", player, new SaveSheetRequest(List.of(fields)));
  }

  SheetResultDto clear(UUID player, FieldRefDto... fields) {
    return manual.clear(
        roomId, user(commissioner), "m1", player, new ClearFieldsRequest(List.of(fields)));
  }

  BigDecimal override(UUID poolPlayer, String stat) {
    return stats.overridesFor(roomId, "m1", poolPlayer).stream()
        .filter(o -> o.stat().equals(stat) && o.eventType().isEmpty())
        .map(OverrideValue::value)
        .findFirst()
        .orElse(null);
  }

  int overrideRows() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM scoring_stat_overrides WHERE room_id = ?", Integer.class, roomId);
  }

  int overrideLogRows() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM scoring_override_log WHERE room_id = ?", Integer.class, roomId);
  }

  BigDecimal stored(UUID poolPlayer) {
    var rows =
        jdbc.queryForList(
            "SELECT total FROM scoring_points WHERE room_id = ? AND match_id = 'm1'"
                + " AND pool_player_id = ?",
            BigDecimal.class,
            roomId,
            poolPlayer);
    return rows.isEmpty() ? null : rows.get(0);
  }

  /** What the evaluator gives for these effective stats under the room's default rules. */
  BigDecimal evaluated(Map<String, String> values) {
    var inputs = port.ruleInputs(roomId);
    Map<String, BigDecimal> map = new java.util.HashMap<>();
    values.forEach((k, v) -> map.put(k, new BigDecimal(v)));
    return PointsEvaluator.evaluate(
            inputs.defaultRules(),
            inputs.appearanceRule(),
            inputs.stats(),
            player.role(),
            new StatLine(map, Map.of()))
        .total();
  }

  // ---- AC-2 ----

  @Test
  void savingFiftyTwoOverProviderFortySixStoresOneOverrideAndTheTotalFollowsTheEvaluator() {
    provider(datasetId, "m1", player.sourcePlayerId(), "46");

    var result = save(player.poolPlayerId(), field("runs", "52"));

    assertThat(overrideRows()).isEqualTo(1);
    assertThat(override(player.poolPlayerId(), "runs")).isEqualByComparingTo("52");
    BigDecimal expected = evaluated(Map.of("runs", "52", "in-playing-xi", "1"));
    assertThat(stored(player.poolPlayerId())).isEqualByComparingTo(expected);
    assertThat(result.after()).isEqualByComparingTo(expected);
    assertThat(result.before())
        .isEqualByComparingTo(evaluated(Map.of("runs", "46", "in-playing-xi", "1")));
    assertThat(result.providerOnly()).isEqualByComparingTo(result.before());
    assertThat(result.changedFields()).isEqualTo(1);
    assertThat(result.franchise()).isNull();
  }

  @Test
  void aSaveKeepsAnExistingOverrideOnAnotherFieldAndBlankFieldsCreateNone() {
    provider(datasetId, "m1", player.sourcePlayerId(), "46");
    save(player.poolPlayerId(), field("runs", "52"));

    save(player.poolPlayerId(), field("fours", "6"), field("sixes", null), field("runs", null));

    assertThat(override(player.poolPlayerId(), "runs")).isEqualByComparingTo("52");
    assertThat(override(player.poolPlayerId(), "fours")).isEqualByComparingTo("6");
    assertThat(override(player.poolPlayerId(), "sixes")).isNull();
    assertThat(overrideRows()).isEqualTo(2);
  }

  @Test
  void aTypedFieldIsStoredUnderItsEventType() {
    save(player.poolPlayerId(), typed("wickets", "lbw", "2"));

    var row = stats.overridesFor(roomId, "m1", player.poolPlayerId()).get(0);
    assertThat(row.stat()).isEqualTo("wickets");
    assertThat(row.eventType()).isEqualTo("lbw");
    assertThat(row.value()).isEqualByComparingTo("2");
  }

  // ---- AC-6 ----

  @Test
  void oneBadFieldRefusesTheWholeSheetNamingItsRangeAndWritesNothing() {
    provider(datasetId, "m1", player.sourcePlayerId(), "46");
    BigDecimal pointsBefore = stored(player.poolPlayerId());

    var thrown =
        thrownBy(() -> save(player.poolPlayerId(), field("fours", "6"), field("runs", "-1")));

    assertThat(thrown.code()).isEqualTo("invalid_stat_sheet");
    assertThat(thrown.fields()).hasSize(1);
    SheetFieldErrorDto bad = thrown.fields().get(0);
    assertThat(bad.stat()).isEqualTo("runs");
    assertThat(bad.reason()).isEqualTo("below_min");
    assertThat(bad.min()).isEqualByComparingTo("0");
    assertThat(bad.max()).isEqualByComparingTo("1000000");
    assertThat(thrown.getMessage()).isEqualTo("1 field needs fixing. Nothing was saved.");
    assertThat(overrideRows()).isZero();
    assertThat(overrideLogRows()).isZero();
    assertThat(stored(player.poolPlayerId())).isEqualByComparingTo(pointsBefore);
  }

  @Test
  void everyKindOfBadFieldIsNamedWithItsReason() {
    Map<FieldValueDto, String> cases = new java.util.LinkedHashMap<>();
    cases.put(field("nope", "1"), "unknown_stat");
    cases.put(typed("wickets", "zz", "1"), "unknown_event_type");
    cases.put(field("wickets", "1"), "unknown_event_type");
    cases.put(typed("runs", "lbw", "1"), "unknown_event_type");
    cases.put(field("runs", "2.5"), "not_whole");
    cases.put(field("runs", "1000001"), "above_max");
    cases.put(field("economy", "1.23456"), "too_many_decimals");
    cases.forEach(
        (bad, reason) -> {
          var thrown = thrownBy(() -> save(player.poolPlayerId(), bad));
          assertThat(thrown.fields())
              .extracting(SheetFieldErrorDto::reason)
              .containsExactly(reason);
        });
    var many = thrownBy(() -> save(player.poolPlayerId(), field("runs", "-1"), field("nope", "1")));
    assertThat(many.fields()).hasSize(2);
    assertThat(many.getMessage()).isEqualTo("2 fields need fixing. Nothing was saved.");
    var twice = thrownBy(() -> save(player.poolPlayerId(), field("runs", "1"), field("runs", "2")));
    assertThat(twice.fields())
        .extracting(SheetFieldErrorDto::reason)
        .containsExactly("duplicate_field");
    assertThat(overrideRows()).isZero();
  }

  @Test
  void aDecimalStatTakesDecimals() {
    save(player.poolPlayerId(), field("economy", "7.25"));

    assertThat(override(player.poolPlayerId(), "economy")).isEqualByComparingTo("7.25");
  }

  // ---- AC-7 ----

  @Test
  void fiftyTwoInEveryFormStoresTheSameValueAndAResaveWritesNoLog() {
    save(player.poolPlayerId(), field("runs", "52"));
    int logAfterFirst = overrideLogRows();
    assertThat(logAfterFirst).isEqualTo(1);

    var again = save(player.poolPlayerId(), field("runs", "52.0"));
    save(player.poolPlayerId(), field("runs", "52.0000"));

    assertThat(overrideLogRows()).isEqualTo(logAfterFirst);
    assertThat(again.changedFields()).isZero();
    assertThat(again.before()).isEqualByComparingTo(again.after());
    assertThat(
            jdbc.queryForObject(
                "SELECT value::text FROM scoring_stat_overrides WHERE room_id = ? AND stat = 'runs'",
                String.class,
                roomId))
        .isEqualTo("52");
  }

  // ---- AC-8, AC-9, AC-13 ----

  @Test
  void matchRefusals() {
    UUID p = player.poolPlayerId();
    var request = new SaveSheetRequest(List.of(field("runs", "1")));

    assertThatThrownBy(() -> manual.save(roomId, user(commissioner), "zz", p, request))
        .isInstanceOfSatisfying(
            MatchRefusedException.class,
            e -> {
              assertThat(e.code()).isEqualTo("unknown_match");
              assertThat(e.conflict()).isFalse();
            });

    clock.freeze(START.minusSeconds(1));
    assertThatThrownBy(() -> manual.save(roomId, user(commissioner), "m1", p, request))
        .isInstanceOfSatisfying(
            MatchRefusedException.class,
            e -> {
              assertThat(e.code()).isEqualTo("match_not_started");
              assertThat(e.conflict()).isTrue();
            });
    assertThatThrownBy(
            () ->
                manual.clear(
                    roomId,
                    user(commissioner),
                    "m1",
                    p,
                    new ClearFieldsRequest(List.of(new FieldRefDto("runs", null)))))
        .isInstanceOf(MatchRefusedException.class);

    clock.freeze(START);
    assertThat(manual.save(roomId, user(commissioner), "m1", p, request).changedFields())
        .isEqualTo(1);
    assertThat(overrideRows()).isEqualTo(1);
  }

  @Test
  void aRoomWithNoDatasetHasNoMatches() {
    jdbc.update("DELETE FROM room_pool_players WHERE room_id = ?", roomId);
    jdbc.update("DELETE FROM room_pools WHERE room_id = ?", roomId);

    assertThatThrownBy(
            () ->
                manual.save(
                    roomId,
                    user(commissioner),
                    "m1",
                    UUID.randomUUID(),
                    new SaveSheetRequest(List.of(field("runs", "1")))))
        .isInstanceOfSatisfying(
            MatchRefusedException.class, e -> assertThat(e.code()).isEqualTo("unknown_match"));
    assertThat(manual.matches(roomId, user(commissioner))).isEmpty();
  }

  @Test
  void aPlayerOutsideTheRoomsPoolIsRefusedIncludingAnotherRoomsPoolPlayer() {
    UUID other = otherRoom();
    UUID foreign = port.players(other).get(0).poolPlayerId();

    for (UUID stranger : List.of(UUID.randomUUID(), foreign)) {
      assertThatThrownBy(() -> save(stranger, field("runs", "1")))
          .isInstanceOfSatisfying(
              MatchRefusedException.class, e -> assertThat(e.code()).isEqualTo("unknown_player"));
    }
    assertThat(overrideRows()).isZero();
    assertThat(overrideLogRows()).isZero();
  }

  @Test
  void aSheetWithNoFilledFieldIsRefusedAsEmptyAndAClearOfNothingIsMalformed() {
    var thrown = thrownBy(() -> save(player.poolPlayerId(), field("runs", null)));
    assertThat(thrown.code()).isEqualTo("empty_sheet");
    assertThat(thrown.fields()).isEmpty();
    assertThat(thrownBy(() -> save(player.poolPlayerId())).code()).isEqualTo("empty_sheet");
    assertThatThrownBy(() -> clear(player.poolPlayerId()))
        .isInstanceOf(com.paddleup.scoring.exception.InvalidRequestException.class);
  }

  // ---- access ----

  @Test
  void onlyTheCommissionerSaves() {
    var request = new SaveSheetRequest(List.of(field("runs", "1")));

    assertThatThrownBy(
            () -> manual.save(roomId, user(manager), "m1", player.poolPlayerId(), request))
        .isInstanceOf(RoomForbiddenException.class);
    assertThatThrownBy(
            () -> manual.save(roomId, user(person()), "m1", player.poolPlayerId(), request))
        .isInstanceOf(RoomNotFoundException.class);
    assertThat(overrideRows()).isZero();
  }

  // ---- clear: AC-11, AC-12 ----

  @Test
  void clearingOneOfTwoOverridesLeavesTheOtherAndTheClearedFieldTakesTheProviderValue() {
    provider(datasetId, "m1", player.sourcePlayerId(), "46");
    save(player.poolPlayerId(), field("runs", "52"), field("fours", "6"));

    var result = clear(player.poolPlayerId(), new FieldRefDto("runs", null));

    assertThat(override(player.poolPlayerId(), "runs")).isNull();
    assertThat(override(player.poolPlayerId(), "fours")).isEqualByComparingTo("6");
    assertThat(stored(player.poolPlayerId()))
        .isEqualByComparingTo(evaluated(Map.of("runs", "46", "in-playing-xi", "1", "fours", "6")));
    assertThat(result.changedFields()).isEqualTo(1);
    var entry =
        jdbc.queryForMap(
            "SELECT old_value, new_value FROM scoring_override_log WHERE room_id = ?"
                + " AND stat = 'runs' ORDER BY seq DESC LIMIT 1",
            roomId);
    assertThat((BigDecimal) entry.get("old_value")).isEqualByComparingTo("52");
    assertThat(entry.get("new_value")).isNull();
  }

  @Test
  void clearingTheLastOverrideOfAPlayerWithNoProviderDataRemovesThePointsRow() {
    save(player.poolPlayerId(), field("runs", "52"));
    assertThat(stored(player.poolPlayerId())).isNotNull();

    var result = clear(player.poolPlayerId(), new FieldRefDto("runs", null));

    assertThat(stored(player.poolPlayerId())).isNull();
    assertThat(result.after()).isNull();
    assertThat(result.before()).isNotNull();
    assertThat(overrideRows()).isZero();
  }

  @Test
  void clearingAFieldWithNoOverrideChangesNothingAndLogsNothing() {
    provider(datasetId, "m1", player.sourcePlayerId(), "46");
    BigDecimal pointsBefore = stored(player.poolPlayerId());
    save(player.poolPlayerId(), field("fours", "6"));
    int logRows = overrideLogRows();

    var result = clear(player.poolPlayerId(), new FieldRefDto("runs", null));

    assertThat(result.changedFields()).isZero();
    assertThat(overrideLogRows()).isEqualTo(logRows);
    assertThat(override(player.poolPlayerId(), "fours")).isEqualByComparingTo("6");
    assertThat(pointsBefore).isNotNull();
  }

  @Test
  void clearRefusesAnUnknownFieldWholeAndNamesIt() {
    save(player.poolPlayerId(), field("runs", "52"));

    var thrown =
        thrownBy(
            () ->
                clear(
                    player.poolPlayerId(),
                    new FieldRefDto("runs", null),
                    new FieldRefDto("nope", null)));

    assertThat(thrown.fields()).extracting(SheetFieldErrorDto::stat).containsExactly("nope");
    assertThat(override(player.poolPlayerId(), "runs")).isEqualByComparingTo("52");
  }

  // ---- AC-14 ----

  @Test
  void providerOnlyPointsEqualAnEvaluationWithNoOverrides() {
    provider(datasetId, "m1", player.sourcePlayerId(), "46");

    var result = save(player.poolPlayerId(), field("runs", "52"), field("fours", "6"));

    assertThat(result.providerOnly())
        .isEqualByComparingTo(evaluated(Map.of("runs", "46", "in-playing-xi", "1")));
    assertThat(result.after()).isNotEqualByComparingTo(result.providerOnly());
    assertThat(result.sheet().providerOnly()).isEqualByComparingTo(result.providerOnly());
  }

  private InvalidStatSheetException thrownBy(Runnable action) {
    return org.junit.jupiter.api.Assertions.assertThrows(
        InvalidStatSheetException.class, action::run);
  }
}
