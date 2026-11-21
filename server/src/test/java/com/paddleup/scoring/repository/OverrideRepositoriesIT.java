package com.paddleup.scoring.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.scoring.ScoringFixtures;
import com.paddleup.scoring.model.FieldKey;
import com.paddleup.scoring.model.OverrideLogEntry;
import com.paddleup.scoring.model.OverrideValue;
import com.paddleup.support.AbstractPostgresIT;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** AC-2, AC-4, AC-11, AC-12: per-player override reads, keyed deletes, match summaries, the log. */
class OverrideRepositoriesIT extends AbstractPostgresIT {

  static final Instant NOW = Instant.parse("2027-03-01T10:00:00Z");

  @Autowired JdbcTemplate jdbc;
  @Autowired MatchStatsRepository stats;
  @Autowired OverrideLogRepository log;

  private static OverrideValue value(UUID player, String stat, String type, String v) {
    return new OverrideValue(player, stat, type, new BigDecimal(v));
  }

  @Test
  void overridesForReturnsOnePlayersFieldsAndDeleteRemovesOnlyTheNamedOnes() {
    var fx = new ScoringFixtures(jdbc);
    UUID room = fx.room();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    stats.upsertOverride(room, "m1", value(a, "s1", "", "1"));
    stats.upsertOverride(room, "m1", value(a, "s2", "x", "2"));
    stats.upsertOverride(room, "m1", value(a, "s3", "", "3"));
    stats.upsertOverride(room, "m1", value(b, "s1", "", "9"));

    assertThat(stats.overridesFor(room, "m1", a)).hasSize(3);
    stats.deleteOverrides(room, "m1", a, List.of(new FieldKey("s1", ""), new FieldKey("s2", "x")));

    assertThat(stats.overridesFor(room, "m1", a))
        .extracting(OverrideValue::stat)
        .containsExactly("s3");
    assertThat(stats.overridesFor(room, "m1", b)).hasSize(1);
    assertThat(stats.overridesFor(UUID.randomUUID(), "m1", a)).isEmpty();
  }

  @Test
  void matchStartAndSummariesCountProviderRowsAndOverridePlayersInPlayOrder() {
    var fx = new ScoringFixtures(jdbc);
    UUID room = fx.room();
    UUID dataset = fx.dataset();
    UUID season = fx.seasonPlayer(dataset);
    stats.upsertMatch(dataset, "b", NOW, "Final");
    stats.upsertMatch(dataset, "a", NOW, null);
    stats.upsertMatch(dataset, "c", NOW.minusSeconds(60), null);
    stats.replaceProviderValues(
        dataset,
        "b",
        List.of(
            new com.paddleup.scoring.model.ProviderValue(season, "s", "", BigDecimal.ONE),
            new com.paddleup.scoring.model.ProviderValue(season, "t", "", BigDecimal.ONE)));
    UUID p1 = UUID.randomUUID();
    stats.upsertOverride(room, "b", value(p1, "s", "", "5"));
    stats.upsertOverride(room, "b", value(p1, "t", "", "5"));
    stats.upsertOverride(room, "b", value(UUID.randomUUID(), "s", "", "5"));
    stats.upsertOverride(room, "a", value(p1, "s", "", "5"));

    assertThat(stats.matchStart(dataset, "b")).contains(NOW);
    assertThat(stats.matchStart(dataset, "zz")).isEmpty();
    var summaries = stats.matchSummaries(room, dataset);

    assertThat(summaries).extracting("matchId").containsExactly("c", "a", "b");
    assertThat(summaries.get(0).providerRows()).isZero();
    assertThat(summaries.get(0).overridePlayers()).isZero();
    assertThat(summaries.get(1).overridePlayers()).isEqualTo(1);
    assertThat(summaries.get(2).providerRows()).isEqualTo(2);
    assertThat(summaries.get(2).overridePlayers()).isEqualTo(2);
    assertThat(summaries.get(2).label()).isEqualTo("Final");
  }

  @Test
  void theLogPagesBySeqAndKeepsNullOldAndNewValues() {
    var fx = new ScoringFixtures(jdbc);
    UUID room = fx.room();
    UUID actor = fx.user();
    UUID player = UUID.randomUUID();
    for (int i = 0; i < 3; i++) {
      log.append(
          new OverrideLogEntry(
              0,
              room,
              actor,
              NOW.plusSeconds(i),
              "m1",
              player,
              "s",
              "",
              i == 0 ? null : BigDecimal.valueOf(i),
              i == 2 ? null : BigDecimal.valueOf(i + 1)));
    }

    var first = log.page(room, 0, 2);
    var rest = log.page(room, first.get(1).seq(), 2);

    assertThat(first).hasSize(2);
    assertThat(first.get(0).oldValue()).isNull();
    assertThat(first.get(0).newValue()).isEqualByComparingTo("1");
    assertThat(rest).hasSize(1);
    assertThat(rest.get(0).newValue()).isNull();
    assertThat(rest.get(0).occurredAt()).isEqualTo(NOW.plusSeconds(2));
    assertThat(log.page(UUID.randomUUID(), 0, 10)).isEmpty();
  }
}
