package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-3, AC-8: go-live at the start instant, then lot after lot until ended. */
class AuctionLifecycleIT extends AbstractAuctionIT {

  private String roomState(Room room) {
    return jdbc.queryForObject(
        "SELECT state FROM rooms WHERE id = ?", String.class, UUID.fromString(room.id()));
  }

  @Test
  void theAuctionGoesLiveAtTheStartInstantWithNoRequestAndLotOneIsTheFirstPlayerOfTheFirstSet() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(60));
    schedule(room, T0);

    clock.freeze(T0.minusSeconds(1));
    tick();
    assertThat(state(room, room.manager(0)).get("phase").asText()).isEqualTo("scheduled");
    assertThat(roomState(room)).isEqualTo("auction_scheduled");

    clock.freeze(T0);
    tick();

    JsonNode state = state(room, room.manager(0));
    assertThat(state.get("phase").asText()).isEqualTo("live");
    assertThat(roomState(room)).isEqualTo("auction_live");
    assertThat(state.get("lotCount").asInt()).isEqualTo(6);
    assertThat(state.get("lotOpen").asBoolean()).isTrue();
    JsonNode lot = state.get("lot");
    assertThat(lot.get("no").asInt()).isEqualTo(1);
    assertThat(lot.get("name").asText()).isEqualTo("Player 1");
    assertThat(lot.get("basePrice").asLong()).isEqualTo(20);
    assertThat(lot.get("set").asText()).isEqualTo("Marquee");
    assertThat(Instant.parse(state.get("deadline").asText())).isEqualTo(T0.plusSeconds(20));
  }

  @Test
  void auctionStartedIsTheFirstLivePhaseEventAndHoldsTheSnapshot() {
    Room room = room(2, 6);
    goLive(room);

    JsonNode log = events(room, room.manager(1), 0).get("events");

    assertThat(log.findValuesAsText("kind"))
        .containsExactly("scheduled", "auction_started", "lot_opened");
    JsonNode started = log.get(1).get("payload");
    assertThat(started.get("openSeconds").asInt()).isEqualTo(20);
    assertThat(started.get("bidSeconds").asInt()).isEqualTo(10);
    assertThat(started.get("gapSeconds").asInt()).isEqualTo(5);
    assertThat(started.get("ladder")).hasSize(4);
    assertThat(started.get("ladder").get(0).get("step").asInt()).isEqualTo(5);
    assertThat(started.get("lots")).hasSize(6);
    assertThat(started.get("purses")).hasSize(2);
    assertThat(started.get("purses").get(room.franchise(0).toString()).asLong()).isEqualTo(1000);
    JsonNode opened = log.get(2);
    assertThat(opened.get("lot").asInt()).isEqualTo(1);
    assertThat(opened.get("payload").get("card").get("name").asText()).isEqualTo("Player 1");
    assertThat(Instant.parse(opened.get("payload").get("deadline").asText()))
        .isEqualTo(T0.plusSeconds(20));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auction_lots WHERE room_id = ?",
                Integer.class,
                UUID.fromString(room.id())))
        .isEqualTo(6);
  }

  @Test
  void atTheDeadlineTheLeaderWinsAndIsDebitedThenTheNextLotOpensAfterTheGap() {
    Room room = room(2, 6);
    goLive(room);
    assertThat(outcome(bid(room, room.manager(0), 1, 20))).isEqualTo("accepted");
    assertThat(outcome(bid(room, room.manager(1), 1, 25))).isEqualTo("accepted");

    advance(9);
    tick();
    assertThat(state(room, room.manager(0)).get("lotOpen").asBoolean()).isTrue();
    advance(1);
    tick();

    JsonNode gap = state(room, room.manager(0));
    assertThat(gap.get("lotOpen").asBoolean()).isFalse();
    assertThat(gap.get("lastResult").get("sold").asBoolean()).isTrue();
    assertThat(gap.get("lastResult").get("franchiseId").asText())
        .isEqualTo(room.franchise(1).toString());
    assertThat(gap.get("lastResult").get("amount").asLong()).isEqualTo(25);
    assertThat(gap.get("nextLot").get("no").asInt()).isEqualTo(2);
    JsonNode winner = null;
    for (JsonNode s : gap.get("standings")) {
      if (s.get("franchiseId").asText().equals(room.franchise(1).toString())) {
        winner = s;
      }
    }
    assertThat(winner.get("purse").asLong()).isEqualTo(975);
    assertThat(winner.get("squad")).hasSize(1);
    assertThat(winner.get("squad").get(0).get("price").asLong()).isEqualTo(25);

    advance(4);
    tick();
    assertThat(state(room, room.manager(0)).get("lotOpen").asBoolean()).isFalse();
    advance(1);
    tick();
    JsonNode two = state(room, room.manager(0));
    assertThat(two.get("lotOpen").asBoolean()).isTrue();
    assertThat(two.get("lot").get("no").asInt()).isEqualTo(2);
    assertThat(two.get("currentBid").asLong()).isZero();
    assertThat(Instant.parse(two.get("deadline").asText()))
        .isEqualTo(clock.instant().plusSeconds(20));
  }

  @Test
  void aLotWithNoBidsIsUnsoldAndTheLastCloseEndsTheAuctionWithUnsoldListed() {
    Room room = room(2, 3);
    goLive(room);

    // Lot 1 is sold; lots 2 and 3 get no bids.
    bid(room, room.manager(0), 1, 20);
    advance(10);
    tick();
    advance(5);
    tick();
    advance(20);
    tick();
    advance(5);
    tick();
    advance(20);
    tick();

    JsonNode end = state(room, room.commissioner());
    assertThat(end.get("phase").asText()).isEqualTo("ended");
    assertThat(end.get("lotOpen").asBoolean()).isFalse();
    assertThat(end.get("unsold")).hasSize(2);
    assertThat(end.get("unsold").get(0).asInt()).isEqualTo(2);
    assertThat(end.get("unsold").get(1).asInt()).isEqualTo(3);
    assertThat(eventCount(room, "lot_sold")).isEqualTo(1);
    assertThat(eventCount(room, "lot_unsold")).isEqualTo(2);
    assertThat(eventCount(room, "auction_ended")).isEqualTo(1);
    long seq = lastSeq(room);
    advance(1000);
    tick();
    assertThat(lastSeq(room)).isEqualTo(seq);
    // The room stays auction_live: season start belongs to a later spec.
    assertThat(roomState(room)).isEqualTo("auction_live");
    // Lot 3 closed with the auction: a late bid on it is too late; a lot past the end is a 409.
    assertThat(outcome(bid(room, room.manager(0), 3, 20))).isEqualTo("rejected:lot_closed");
    assertThat(bid(room, room.manager(0), 4, 20).getStatusCode().value()).isEqualTo(409);
  }
}
