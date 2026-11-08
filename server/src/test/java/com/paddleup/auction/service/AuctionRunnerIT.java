package com.paddleup.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.repository.AuctionEventRepository;
import com.paddleup.auction.util.Ladder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** AC-9, AC-10, AC-11: one writer, persist before apply, resend-safe. */
class AuctionRunnerIT extends AbstractAuctionIT {

  @MockitoSpyBean AuctionEventRepository eventLog;
  @MockitoSpyBean AuctionBroadcaster broadcaster;
  @org.springframework.beans.factory.annotation.Autowired AuctionRegistry registry;

  @Test
  void aForcedLogWriteFailureReturnsAnErrorBroadcastsNothingAndLeavesStateUnchanged() {
    Room room = room(2, 6);
    goLive(room);
    long seqBefore = lastSeq(room);
    JsonNode before = state(room, room.commissioner());
    org.mockito.Mockito.clearInvocations(broadcaster);

    doThrow(new DataAccessResourceFailureException("disk gone"))
        .when(eventLog)
        .append(any(), any());
    var failed = bid(room, room.manager(0), 1, 20);
    org.mockito.Mockito.reset(eventLog);

    assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(body(failed).get("error").asText()).isEqualTo("log_unavailable");
    verify(broadcaster, never()).events(any(), any(), any());
    assertThat(lastSeq(room)).isEqualTo(seqBefore);
    JsonNode after = state(room, room.commissioner());
    assertThat(after.get("lastSeq").asLong()).isEqualTo(before.get("lastSeq").asLong());
    assertThat(after.get("currentBid").asLong()).isZero();
    assertThat(after.has("leaderFranchiseId")).isFalse();
    assertThat(after.get("deadline")).isEqualTo(before.get("deadline"));

    // The log stays gap-free: the next accepted bid takes the very next seq.
    var retry = bid(room, room.manager(0), 1, 20);
    assertThat(outcome(retry)).isEqualTo("accepted");
    assertThat(body(retry).get("seq").asLong()).isEqualTo(seqBefore + 1);
    assertThat(lastSeq(room)).isEqualTo(seqBefore + 1);
  }

  @Test
  void liveStateEqualsTheReplayOfTheStoredLogWhenTheClockHasNanoseconds() {
    Room room = room(2, 6);
    goLive(room);
    clock.freeze(T0.plusSeconds(1).plusNanos(123_456_789));
    bid(room, room.manager(0), 1, 20);
    clock.freeze(T0.plusSeconds(11).plusNanos(123_456_789));
    tick();
    assertThat(eventCount(room, "lot_sold")).isEqualTo(1);

    UUID roomId = UUID.fromString(room.id());
    var live = registry.runner(roomId).state();
    var replayed =
        AuctionReducer.replay(AuctionState.initial(roomId, live.catalog()), eventLog.all(roomId));

    assertThat(live).isEqualTo(replayed);
    assertThat(live.nextOpenAt().getNano() % 1000).isZero();
  }

  @Test
  void aStuckRoomDoesNotFreezeTheClockOfOtherRoomsAndItsTicksDoNotPileUp() throws Exception {
    Room stuck = room(2, 6);
    goLive(stuck);
    Room other = room(2, 6);
    goLive(other);
    bid(stuck, stuck.manager(0), 1, 20);
    bid(other, other.manager(0), 1, 20);
    advance(10);
    UUID stuckId = UUID.fromString(stuck.id());
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    doAnswer(
            call -> {
              if (stuckId.equals(call.getArgument(0))) {
                entered.countDown();
                release.await(30, java.util.concurrent.TimeUnit.SECONDS);
              }
              return call.callRealMethod();
            })
        .when(eventLog)
        .append(any(), any());
    try {
      ticker.requestTicks();

      assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      waitUntil(() -> eventCount(other, "lot_sold") == 1);
      assertThat(eventCount(stuck, "lot_sold")).isZero();
      // Ticks asked for while one is running share one queued tick instead of piling up.
      var queued = registry.runner(stuckId).tick();
      assertThat(registry.runner(stuckId).tick()).isSameAs(queued);
    } finally {
      release.countDown();
      org.mockito.Mockito.reset(eventLog);
    }
    waitUntil(() -> eventCount(stuck, "lot_sold") == 1);
  }

  private static void waitUntil(java.util.function.BooleanSupplier condition)
      throws InterruptedException {
    long give = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    while (!condition.getAsBoolean() && System.nanoTime() < give) {
      Thread.sleep(20);
    }
    assertThat(condition.getAsBoolean()).as("condition met within 5 s").isTrue();
  }

  @Test
  void twentyRoundsOfConcurrentSameAmountBidsGiveExactlyOneAcceptancePerRound() throws Exception {
    Room room = room(3, 6);
    goLive(room);
    ExecutorService pool = Executors.newFixedThreadPool(3);
    try {
      long current = 0;
      int leader = -1;
      for (int round = 0; round < 20; round++) {
        long amount =
            current == 0
                ? 20
                : Ladder.next(
                    List.of(
                        new com.paddleup.auction.model.Step(0, 5),
                        new com.paddleup.auction.model.Step(100, 10),
                        new com.paddleup.auction.model.Step(200, 20),
                        new com.paddleup.auction.model.Step(500, 25)),
                    current);
        List<Integer> racers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
          if (i != leader) {
            racers.add(i);
          }
        }
        var barrier = new CyclicBarrier(racers.size());
        long bidAmount = amount;
        List<Future<String>> results = new ArrayList<>();
        for (int racer : racers) {
          Callable<String> call =
              () -> {
                barrier.await();
                return outcome(bid(room, room.manager(racer), 1, bidAmount));
              };
          results.add(pool.submit(call));
        }
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
          outcomes.add(f.get());
        }
        assertThat(outcomes.stream().filter("accepted"::equals)).as("round " + round).hasSize(1);
        assertThat(outcomes.stream().filter("rejected:outbid"::equals))
            .as("round " + round)
            .hasSize(racers.size() - 1);
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM auction_events WHERE room_id = ? AND kind ="
                        + " 'bid_accepted' AND (payload->>'amount')::bigint = ?",
                    Integer.class,
                    UUID.fromString(room.id()),
                    amount))
            .as("round " + round)
            .isEqualTo(1);
        JsonNode s = state(room, room.commissioner());
        current = s.get("currentBid").asLong();
        assertThat(current).isEqualTo(amount);
        UUID leaderId = UUID.fromString(s.get("leaderFranchiseId").asText());
        leader = room.franchises().indexOf(leaderId);
      }
    } finally {
      pool.shutdownNow();
    }
    assertThat(eventCount(room, "bid_accepted")).isEqualTo(20);
  }

  @Test
  void aRepeatedIntentIdReturnsTheFirstOutcomeAndWritesOneEvent() {
    Room room = room(2, 6);
    goLive(room);
    UUID intent = UUID.randomUUID();

    var first = bid(room, room.manager(0), intent, 1, 20);
    long seq = lastSeq(room);
    var again = bid(room, room.manager(0), intent, 1, 20);

    assertThat(outcome(first)).isEqualTo("accepted");
    assertThat(outcome(again)).isEqualTo("accepted");
    assertThat(body(again).get("seq").asLong()).isEqualTo(body(first).get("seq").asLong());
    assertThat(body(again).get("deadline")).isEqualTo(body(first).get("deadline"));
    assertThat(lastSeq(room)).isEqualTo(seq);
    assertThat(eventCount(room, "bid_accepted")).isEqualTo(1);

    // A rejected intent resent keeps its first answer, even after it would now be valid.
    UUID wrong = UUID.randomUUID();
    assertThat(outcome(bid(room, room.manager(1), wrong, 1, 7))).isEqualTo("rejected:outbid");
    assertThat(outcome(bid(room, room.manager(1), wrong, 1, 7))).isEqualTo("rejected:outbid");
    assertThat(lastSeq(room)).isEqualTo(seq);
  }
}
