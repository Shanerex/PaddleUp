package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import com.paddleup.auction.StompProbe;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/** AC-7, AC-9, AC-13 (auction screen): the server names the next bid; the socket heartbeats. */
class AuctionNextBidIT extends AbstractAuctionIT {

  private static final String ORIGIN = "http://localhost:3000";

  @LocalServerPort int port;

  @Test
  void stateCarriesTheNextAcceptedAmountAtEveryStep() {
    Room room = room(2, 6);
    goLive(room);

    JsonNode open = state(room, room.manager(0));
    assertThat(open.get("nextBid").asLong()).isEqualTo(open.get("lot").get("basePrice").asLong());

    for (int i = 0; i < 6; i++) {
      long next = state(room, room.manager(0)).get("nextBid").asLong();
      var who = room.manager(i % 2);
      assertThat(outcome(bid(room, who, 1, next))).isEqualTo("accepted");
      long after = state(room, who).get("nextBid").asLong();
      assertThat(after).isGreaterThan(next);
      assertThat(outcome(bid(room, room.manager((i + 1) % 2), 1, after - 1)))
          .startsWith("rejected");
      assertThat(state(room, who).get("nextBid").asLong()).isEqualTo(after);
    }
  }

  @Test
  void noLotOpenMeansNoNextBid() {
    Room room = room(2, 6);
    goLive(room);
    advance(20);
    tick();

    JsonNode gap = state(room, room.manager(0));

    assertThat(gap.get("lotOpen").asBoolean()).isFalse();
    assertThat(gap.has("nextBid")).isFalse();
  }

  @Test
  void lotOpenedAndBidAcceptedBroadcastsCarryTheNextBid() throws Exception {
    Room room = room(2, 6);
    goLive(room);
    try (StompProbe probe =
        StompProbe.connect(json, port, room.manager(0).cookie(), null, ORIGIN)) {
      probe.subscribe("/topic/auctions/" + room.id());
      Thread.sleep(300);

      long first = state(room, room.manager(0)).get("nextBid").asLong();
      bid(room, room.manager(1), 1, first);
      JsonNode accepted = kind(probe, "bid_accepted");
      long expected = state(room, room.manager(0)).get("nextBid").asLong();
      assertThat(accepted.get("payload").get("nextBid").asLong()).isEqualTo(expected);
      assertThat(outcome(bid(room, room.manager(0), 1, expected))).isEqualTo("accepted");

      advance(20);
      tick();
      advance(5);
      tick();
      JsonNode opened = kind(probe, "lot_opened");
      assertThat(opened.get("payload").get("nextBid").asLong())
          .isEqualTo(opened.get("payload").get("card").get("basePrice").asLong());
      assertThat(state(room, room.manager(0)).get("nextBid").asLong())
          .isEqualTo(opened.get("payload").get("nextBid").asLong());
    }
  }

  @Test
  void theBrokerAgreesTenSecondHeartbeatsEachWay() throws Exception {
    Room room = room(2, 6);
    goLive(room);
    try (StompProbe probe =
        StompProbe.connect(json, port, room.manager(0).cookie(), null, ORIGIN, true)) {
      assertThat(probe.connected()).isTrue();
      probe.subscribe("/topic/auctions/" + room.id());
      assertThat(probe.heartbeat()).containsExactly(10_000, 10_000);
      assertThat(probe.connected()).isTrue();
      assertThat(probe.nextError(100)).isNull();
    }
  }

  private JsonNode kind(StompProbe probe, String kind) throws Exception {
    for (int i = 0; i < 20; i++) {
      JsonNode message = probe.next(1000);
      if (message != null && message.has("kind") && kind.equals(message.get("kind").asText())) {
        return message;
      }
    }
    throw new AssertionError("no " + kind + " event arrived");
  }
}
