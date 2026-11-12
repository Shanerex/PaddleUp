package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.scoring.ScoringIT;
import com.paddleup.scoring.repository.RuleSetRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** A recompute reads the rules under the rule row's lock, so it never scores with an old set. */
class RecomputeLockIT extends ScoringIT {

  @Autowired RuleSetRepository sets;
  @Autowired TransactionTemplate tx;

  @Test
  void aRecomputeWaitsForARuleEditInFlightAndScoresWithTheCommittedRules() throws Exception {
    provider(datasetId, "m1", player.sourcePlayerId(), "30");
    assertThat(scoring.points(roomId, "m1").get(0).total()).isEqualByComparingTo("34");
    sets.insertIfAbsent(roomId, clock.instant());
    var holding = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> edit =
          pool.submit(
              () ->
                  tx.executeWithoutResult(
                      s -> {
                        sets.lock(roomId);
                        sets.update(
                            roomId,
                            runsWorth(port.ruleInputs(roomId).defaultRules(), "2"),
                            0,
                            user(commissioner).value(),
                            clock.instant());
                        holding.countDown();
                        try {
                          release.await();
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                      }));
      holding.await();
      Future<?> recompute = pool.submit(() -> scoring.recompute(roomId, "m1"));
      Thread.sleep(400);
      assertThat(recompute.isDone()).as("recompute waits for the edit's lock").isFalse();

      release.countDown();
      edit.get(10, TimeUnit.SECONDS);
      recompute.get(20, TimeUnit.SECONDS);

      var points = scoring.points(roomId, "m1").get(0);
      assertThat(points.ruleSetVersion()).isEqualTo(1);
      assertThat(points.total()).isEqualByComparingTo("64");
    } finally {
      pool.shutdownNow();
    }
  }
}
