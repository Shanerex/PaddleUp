package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.rooms.dto.ScoringPlayerDto;
import com.paddleup.scoring.model.OverrideValue;
import com.paddleup.scoring.model.ProviderValue;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-9: the override, else the provider value; an added player takes manual values only. */
class EffectiveStatsTest {

  final UUID season = UUID.randomUUID();
  final UUID sourced = UUID.randomUUID();
  final UUID added = UUID.randomUUID();
  final UUID quiet = UUID.randomUUID();
  final List<ScoringPlayerDto> players =
      List.of(
          new ScoringPlayerDto(sourced, season, "BAT"),
          new ScoringPlayerDto(added, null, "BAT"),
          new ScoringPlayerDto(quiet, UUID.randomUUID(), "BAT"));

  ProviderValue provider(String stat, String type, String value) {
    return new ProviderValue(season, stat, type, new BigDecimal(value));
  }

  OverrideValue manual(UUID pool, String stat, String type, String value) {
    return new OverrideValue(pool, stat, type, new BigDecimal(value));
  }

  @Test
  void theProviderValueIsUsedThroughTheSourcePlayerWhenThereIsNoOverride() {
    var lines =
        EffectiveStats.forPlayers(
            players, List.of(provider("s", "", "40"), provider("w", "x", "2")), List.of());

    assertThat(lines).containsOnlyKeys(sourced);
    assertThat(lines.get(sourced).stat("s")).isEqualByComparingTo("40");
    assertThat(lines.get(sourced).event("w", "x")).isEqualByComparingTo("2");
  }

  @Test
  void anOverrideBeatsTheProviderValueForTheSameStatAndEventType() {
    var lines =
        EffectiveStats.forPlayers(
            players,
            List.of(provider("s", "", "40"), provider("w", "x", "2"), provider("t", "", "7")),
            List.of(manual(sourced, "s", "", "55"), manual(sourced, "w", "x", "3")));

    var line = lines.get(sourced);
    assertThat(line.stat("s")).isEqualByComparingTo("55");
    assertThat(line.event("w", "x")).isEqualByComparingTo("3");
    assertThat(line.stat("t")).isEqualByComparingTo("7");
  }

  @Test
  void anOverrideAddsAStatTheProviderDidNotSendAndIsSparse() {
    var lines =
        EffectiveStats.forPlayers(
            players, List.of(provider("s", "", "40")), List.of(manual(sourced, "u", "", "1")));

    assertThat(lines.get(sourced).stat("s")).isEqualByComparingTo("40");
    assertThat(lines.get(sourced).stat("u")).isEqualByComparingTo("1");
    assertThat(lines.get(sourced).stats()).containsOnlyKeys("s", "u");
  }

  @Test
  void aPlayerAddedByHandTakesManualValuesOnly() {
    var lines =
        EffectiveStats.forPlayers(
            players, List.of(provider("s", "", "40")), List.of(manual(added, "s", "", "9")));

    assertThat(lines).containsOnlyKeys(sourced, added);
    assertThat(lines.get(added).stat("s")).isEqualByComparingTo("9");
    assertThat(lines.get(added).stats()).containsOnlyKeys("s");
  }

  @Test
  void aPlayerWithNoValueGetsNoLineAndAnOverrideForAnotherRoomsPlayerIsIgnored() {
    var lines =
        EffectiveStats.forPlayers(
            players, List.of(), List.of(manual(UUID.randomUUID(), "s", "", "9")));

    assertThat(lines).isEmpty();
  }
}
