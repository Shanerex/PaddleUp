package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.paddleup.scoring.ScoringIT;
import com.paddleup.scoring.dto.FieldValueDto;
import com.paddleup.scoring.dto.PlayerPointsDto;
import com.paddleup.scoring.dto.SaveSheetRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** AC-15, AC-16: a failed recompute rolls the save back; simultaneous saves end consistent. */
class ManualStatsAtomicityIT extends ScoringIT {

  @Autowired ManualStatsService manual;
  @MockitoSpyBean ScoringService spied;

  @BeforeEach
  void started() {
    clock.freeze(START.plusSeconds(60));
  }

  private SaveSheetRequest runs(String value) {
    return new SaveSheetRequest(List.of(new FieldValueDto("runs", null, new BigDecimal(value))));
  }

  private int count(String table) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE room_id = ?", Integer.class, roomId);
  }

  @Test
  void aFailingRecomputeRollsBackTheOverrideAndItsLogEntry() {
    provider(datasetId, "m1", player.sourcePlayerId(), "46");
    BigDecimal pointsBefore = spied.points(roomId, "m1").get(0).total();
    doThrow(new IllegalStateException("boom")).when(spied).recompute(any(), any());

    assertThatThrownBy(
            () -> manual.save(roomId, user(commissioner), "m1", player.poolPlayerId(), runs("52")))
        .isInstanceOf(IllegalStateException.class);

    assertThat(count("scoring_stat_overrides")).isZero();
    assertThat(count("scoring_override_log")).isZero();
    org.mockito.Mockito.reset(spied);
    assertThat(spied.points(roomId, "m1").get(0).total()).isEqualByComparingTo(pointsBefore);
  }

  @Test
  void simultaneousSavesForOneMatchEndWithPointsEqualToAFreshRecompute() throws Exception {
    var players = port.players(roomId);
    int n = players.size();
    var pool = Executors.newFixedThreadPool(n);
    var start = new CountDownLatch(1);
    try {
      List<Future<?>> saves = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        var target = players.get(i).poolPlayerId();
        String value = String.valueOf(10 + i);
        saves.add(
            pool.submit(
                () -> {
                  start.await();
                  return manual.save(roomId, user(commissioner), "m1", target, runs(value));
                }));
      }
      start.countDown();
      for (Future<?> save : saves) {
        save.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    List<PlayerPointsDto> stored = byPlayer(scoring.points(roomId, "m1"));
    assertThat(stored).hasSize(n);
    scoring.recompute(roomId, "m1");
    List<PlayerPointsDto> fresh = byPlayer(scoring.points(roomId, "m1"));
    assertThat(stored).usingRecursiveComparison().ignoringFields("ruleSetVersion").isEqualTo(fresh);
    assertThat(count("scoring_override_log")).isEqualTo(n);
  }

  @Test
  void twoSavesOfTheSameFieldTakeTheLockFirstSoTheLogFormsAChain() throws Exception {
    var target = player.poolPlayerId();
    int writers = 6;
    var pool = Executors.newFixedThreadPool(writers);
    var start = new CountDownLatch(1);
    try {
      List<Future<?>> saves = new ArrayList<>();
      for (int i = 0; i < writers; i++) {
        String value = String.valueOf(10 + i);
        saves.add(
            pool.submit(
                () -> {
                  start.await();
                  return manual.save(roomId, user(commissioner), "m1", target, runs(value));
                }));
      }
      start.countDown();
      for (Future<?> save : saves) {
        save.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    var log =
        jdbc.queryForList(
            "SELECT old_value, new_value FROM scoring_override_log WHERE room_id = ? ORDER BY seq",
            roomId);
    assertThat(log).hasSize(writers);
    assertThat(log.get(0).get("old_value")).isNull();
    for (int i = 1; i < writers; i++) {
      assertThat((BigDecimal) log.get(i).get("old_value"))
          .isEqualByComparingTo((BigDecimal) log.get(i - 1).get("new_value"));
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT value FROM scoring_stat_overrides WHERE room_id = ?",
                BigDecimal.class,
                roomId))
        .isEqualByComparingTo((BigDecimal) log.get(writers - 1).get("new_value"));
  }

  private static List<PlayerPointsDto> byPlayer(List<PlayerPointsDto> list) {
    return list.stream().sorted(Comparator.comparing(p -> p.poolPlayerId().toString())).toList();
  }
}
