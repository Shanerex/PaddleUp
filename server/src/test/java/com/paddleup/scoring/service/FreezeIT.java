package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.scoring.ScoringIT;
import com.paddleup.scoring.exception.RuleSetConflictException;
import com.paddleup.scoring.repository.RuleSetRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** AC-14, AC-18: a stale tab, two edits on one version, and an edit racing the freeze. */
class FreezeIT extends ScoringIT {

  @Autowired RuleSetService rules;
  @Autowired RuleSetRepository sets;
  @Autowired TransactionTemplate tx;

  private RuleSetDto defaults() {
    return port.ruleInputs(roomId).defaultRules();
  }

  /** The outcome of one call: null when it succeeded, else the conflict code. */
  private String outcome(Callable<?> call) throws Exception {
    try {
      call.call();
      return null;
    } catch (RuleSetConflictException e) {
      return e.code();
    }
  }

  @Test
  void aVersionReadBeforeTheStartIsRejectedAfterItAs409FrozenNotStale() {
    var seen = rules.read(roomId, user(commissioner));
    assertThat(seen.frozen()).isFalse();
    clock.freeze(START);

    var thrown =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuleSetConflictException.class,
            () ->
                rules.edit(
                    roomId, user(commissioner), seen.version(), runsWorth(seen.rules(), "2")));

    assertThat(thrown.code()).isEqualTo("frozen");
    assertThat(logCount(roomId)).isZero();
    assertThat(rules.read(roomId, user(manager)).status()).isEqualTo("pack-default");
  }

  @Test
  void aStaleRequestAtTheFreezeInstantStillRecordsTheFreezeSoARescheduleCannotThawIt()
      throws Exception {
    rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), "2"));
    clock.freeze(START);

    var stale =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuleSetConflictException.class,
            () -> rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), "3")));
    assertThat(stale.code()).isEqualTo("stale_version");

    assertThat(
            jdbc.queryForObject(
                "SELECT frozen_at FROM scoring_rule_sets WHERE room_id = ?",
                java.sql.Timestamp.class,
                roomId))
        .isNotNull();
    matches.upsertMatch(datasetId, "m1", START.plusSeconds(86400));
    var later =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuleSetConflictException.class,
            () -> rules.edit(roomId, user(commissioner), 1, runsWorth(defaults(), "4")));
    assertThat(later.code()).isEqualTo("frozen");
    assertThat(version(roomId)).isEqualTo(1);
  }

  @Test
  void twoEditsOnTheSameVersionOneWinsAndTheOtherIsStale() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    try {
      var start = new CountDownLatch(1);
      List<Future<String>> results = new ArrayList<>();
      for (String points : List.of("2", "3")) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return outcome(
                      () ->
                          rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), points)));
                }));
      }
      start.countDown();
      List<String> codes = new ArrayList<>();
      for (var f : results) {
        codes.add(f.get(20, TimeUnit.SECONDS));
      }

      assertThat(codes).containsExactlyInAnyOrder(null, "stale_version");
      assertThat(version(roomId)).isEqualTo(1);
      assertThat(logCount(roomId)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void anEditWaitingOnTheLockWhileTheClockCrossesTheStartIsRejectedWholly() throws Exception {
    sets.insertIfAbsent(roomId, clock.instant());
    var holding = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> holder =
          pool.submit(
              () ->
                  tx.executeWithoutResult(
                      s -> {
                        sets.lock(roomId);
                        holding.countDown();
                        try {
                          release.await();
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                      }));
      holding.await();
      Future<String> edit =
          pool.submit(
              () ->
                  outcome(
                      () -> rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), "2"))));
      Thread.sleep(300);
      assertThat(edit.isDone()).isFalse();

      clock.freeze(START);
      release.countDown();
      holder.get(10, TimeUnit.SECONDS);

      assertThat(edit.get(20, TimeUnit.SECONDS)).isEqualTo("frozen");
      assertThat(version(roomId)).isZero();
      assertThat(logCount(roomId)).isZero();
      assertThat(rules.read(roomId, user(commissioner)).status()).isEqualTo("pack-default");
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void anEditThatWinsTheLockBeforeTheStartIsFullyApplied() throws Exception {
    provider(datasetId, "m1", player.sourcePlayerId(), "30");

    assertThat(outcome(() -> rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), "2"))))
        .isNull();
    clock.freeze(START);

    assertThat(version(roomId)).isEqualTo(1);
    assertThat(logCount(roomId)).isEqualTo(1);
    assertThat(scoring.points(roomId, "m1").get(0).total()).isEqualByComparingTo("64");
    assertThat(rules.read(roomId, user(commissioner)).status()).isEqualTo("custom");
  }

  @Test
  void manyEditsRacingTheClockLeaveEachOneAppliedWhollyOrRejectedWholly() throws Exception {
    provider(datasetId, "m1", player.sourcePlayerId(), "30");
    int threads = 6;
    var pool = Executors.newFixedThreadPool(threads + 1);
    try {
      var start = new CountDownLatch(1);
      List<Future<String>> results = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        final int n = i;
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  // Each caller sends the version it saw, so most are stale or frozen.
                  return outcome(
                      () ->
                          rules.edit(
                              roomId,
                              user(commissioner),
                              n % 2,
                              runsWorth(defaults(), String.valueOf(2 + n))));
                }));
      }
      Future<?> crossing =
          pool.submit(
              () -> {
                try {
                  start.await();
                  Thread.sleep(40);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                clock.freeze(START);
              });
      start.countDown();
      List<String> codes = new ArrayList<>();
      for (var f : results) {
        codes.add(f.get(30, TimeUnit.SECONDS));
      }
      crossing.get(10, TimeUnit.SECONDS);

      long applied = codes.stream().filter(c -> c == null).count();
      assertThat(codes).allMatch(c -> c == null || c.equals("stale_version") || c.equals("frozen"));
      // Whatever got in is wholly there: one version and one log entry each, and the points
      // follow the stored set.
      assertThat(version(roomId)).isEqualTo((int) applied);
      assertThat(logCount(roomId)).isEqualTo((int) applied);
      var view = rules.read(roomId, user(commissioner));
      var runsRule =
          view.rules().rules().stream().filter(r -> r.id().equals("runs-points")).findFirst().get();
      var points = scoring.points(roomId, "m1").get(0);
      assertThat(points.ruleSetVersion()).isEqualTo(view.version());
      if (applied == 0) {
        assertThat(view.status()).isEqualTo("pack-default");
      } else {
        assertThat(points.total())
            .isEqualByComparingTo(
                ((com.paddleup.packs.dto.RuleDto.PerUnit) runsRule)
                    .points()
                    .multiply(new java.math.BigDecimal("30"))
                    .add(new java.math.BigDecimal("4")));
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
