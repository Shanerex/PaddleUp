package com.paddleup.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.paddleup.auction.AbstractAuctionIT;
import com.paddleup.auction.repository.AuctionEventRepository;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Review follow-up: a ready call that meets the start of the auction cannot leave a stale entry.
 */
class AuctionReadyRaceIT extends AbstractAuctionIT {

  @MockitoSpyBean AuctionEventRepository eventLog;
  @Autowired AuctionRegistry registry;
  @Autowired Readiness readiness;

  @Test
  void aReadyCallThatArrivesWhileTheStartIsBeingCommittedIsRefusedAndLeavesNothing()
      throws Exception {
    Room room = room(2, 6);
    UUID roomId = UUID.fromString(room.id());
    clock.freeze(T0.minusSeconds(60));
    assertThat(schedule(room, T0).getStatusCode()).isEqualTo(HttpStatus.OK);
    put(room.manager(0), auction(room, "/ready"), Map.of("ready", true));
    clock.freeze(T0);

    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              if (roomId.equals(call.getArgument(0))) {
                entered.countDown();
                release.await(30, TimeUnit.SECONDS);
              }
              return call.callRealMethod();
            })
        .when(eventLog)
        .append(any(), any());
    CompletableFuture<org.springframework.http.ResponseEntity<String>> late;
    try {
      registry.runner(roomId).tick();
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

      late =
          CompletableFuture.supplyAsync(
              () -> put(room.manager(1), auction(room, "/ready"), Map.of("ready", true)));
      Thread.sleep(300);
    } finally {
      release.countDown();
      org.mockito.Mockito.reset(eventLog);
    }

    var answer = late.get(10, TimeUnit.SECONDS);
    assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(answer).get("error").asText()).isEqualTo("not_scheduled");
    assertThat(readiness.ready(roomId)).isEmpty();
    assertThat(state(room, room.manager(0)).get("ready")).isEmpty();
  }
}
