package com.paddleup.scoring.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.scoring.ScoringFixtures;
import com.paddleup.scoring.dto.RuleDiffDto;
import com.paddleup.scoring.model.RuleLogEntry;
import com.paddleup.scoring.model.RuleLogKind;
import com.paddleup.support.AbstractPostgresIT;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** AC-17, AC-18: the rule row (create, lock, compare-and-set, freeze) and the change log. */
class RuleSetRepositoryIT extends AbstractPostgresIT {

  static final Instant NOW = Instant.parse("2027-03-01T10:00:00Z");

  @Autowired JdbcTemplate jdbc;
  @Autowired RuleSetRepository sets;
  @Autowired RuleLogRepository log;
  @Autowired TransactionTemplate tx;

  private static RuleSetDto rules(String id, String points) {
    return new RuleSetDto(List.of(new RuleDto.PerUnit(id, "s", new BigDecimal(points), "L")));
  }

  @Test
  void noRowUntilTheFirstInsertAndTheInsertIsIdempotent() {
    UUID room = new ScoringFixtures(jdbc).room();
    assertThat(sets.find(room)).isEmpty();

    sets.insertIfAbsent(room, NOW);
    sets.insertIfAbsent(room, NOW.plusSeconds(60));

    var row = sets.find(room).orElseThrow();
    assertThat(row.rules()).isNull();
    assertThat(row.version()).isZero();
    assertThat(row.frozenAt()).isNull();
    assertThat(row.updatedAt()).isEqualTo(NOW);
  }

  @Test
  void aVersionCompareAndSetSavesOnlyFromTheStoredVersion() {
    var fixtures = new ScoringFixtures(jdbc);
    UUID room = fixtures.room();
    UUID by = fixtures.user();
    sets.insertIfAbsent(room, NOW);

    assertThat(sets.update(room, rules("a", "2.5"), 0, by, NOW)).isTrue();
    assertThat(sets.update(room, rules("b", "9"), 0, by, NOW)).isFalse();

    var row = sets.find(room).orElseThrow();
    assertThat(row.version()).isEqualTo(1);
    assertThat(row.rules()).isEqualTo(rules("a", "2.5"));
    assertThat(row.updatedBy()).isEqualTo(by);

    assertThat(sets.update(room, null, 1, by, NOW)).isTrue();
    assertThat(sets.find(room).orElseThrow().rules()).isNull();
    assertThat(sets.find(room).orElseThrow().version()).isEqualTo(2);
  }

  @Test
  void frozenAtIsSetOnceAndKeepsItsFirstInstant() {
    UUID room = new ScoringFixtures(jdbc).room();
    sets.insertIfAbsent(room, NOW);

    sets.markFrozen(room, NOW.plusSeconds(5));
    sets.markFrozen(room, NOW.plusSeconds(99));

    assertThat(sets.find(room).orElseThrow().frozenAt()).isEqualTo(NOW.plusSeconds(5));
  }

  @Test
  void aSecondTransactionWaitsForTheLockAndThenSeesTheFirstsWrite() throws Exception {
    var fixtures = new ScoringFixtures(jdbc);
    UUID room = fixtures.room();
    UUID by = fixtures.user();
    sets.insertIfAbsent(room, NOW);
    var holding = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          pool.submit(
              () ->
                  tx.executeWithoutResult(
                      s -> {
                        sets.lock(room);
                        holding.countDown();
                        await(release);
                        sets.update(room, rules("a", "1"), 0, by, NOW);
                      }));
      holding.await();
      Future<Integer> second =
          pool.submit(() -> tx.execute(s -> sets.lock(room).orElseThrow().version()));
      Thread.sleep(300);
      assertThat(second.isDone()).isFalse();
      release.countDown();
      first.get(10, TimeUnit.SECONDS);

      assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void theLogAppendsAndListsOldestFirstWithTheWholeSets() {
    var fixtures = new ScoringFixtures(jdbc);
    UUID room = fixtures.room();
    UUID actor = fixtures.user();
    var diff = new RuleDiffDto(List.of("b"), List.of(), List.of("a"));

    log.append(
        new RuleLogEntry(
            0,
            room,
            RuleLogKind.POST_FREEZE_CHANGE,
            actor,
            NOW,
            "Agreed on the group chat",
            true,
            rules("a", "1"),
            rules("a", "2"),
            diff));
    log.append(
        new RuleLogEntry(
            0,
            room,
            RuleLogKind.REVERT,
            actor,
            NOW.plusSeconds(1),
            null,
            false,
            rules("a", "2"),
            rules("a", "1"),
            new RuleDiffDto(null, null, List.of("a"))));

    var entries = log.list(room);

    assertThat(entries).hasSize(2);
    var first = entries.get(0);
    assertThat(first.kind()).isEqualTo(RuleLogKind.POST_FREEZE_CHANGE);
    assertThat(first.actor()).isEqualTo(actor);
    assertThat(first.occurredAt()).isEqualTo(NOW);
    assertThat(first.reason()).isEqualTo("Agreed on the group chat");
    assertThat(first.attested()).isTrue();
    assertThat(first.before()).isEqualTo(rules("a", "1"));
    assertThat(first.after()).isEqualTo(rules("a", "2"));
    assertThat(first.diff()).isEqualTo(diff);
    assertThat(first.seq()).isLessThan(entries.get(1).seq());
    assertThat(entries.get(1).reason()).isNull();
    assertThat(log.list(fixtures.room())).isEmpty();
  }
}
