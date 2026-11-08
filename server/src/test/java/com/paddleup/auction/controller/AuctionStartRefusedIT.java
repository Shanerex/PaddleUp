package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-17: a start the room cannot honour is refused once and waits for a reschedule. */
class AuctionStartRefusedIT extends AbstractAuctionIT {

  private String roomState(Room room) {
    return jdbc.queryForObject(
        "SELECT state FROM rooms WHERE id = ?", String.class, UUID.fromString(room.id()));
  }

  @Test
  void aPoolBelowTheMinimumAtTheStartInstantOpensNoLotAndLogsOneRefusal() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(60));
    schedule(room, T0);
    replacePool(room.id(), 1, 20);

    clock.freeze(T0);
    tick();
    tick();
    advance(30);
    tick();

    assertThat(eventCount(room, "start_refused")).isEqualTo(1);
    assertThat(eventCount(room, "auction_started")).isZero();
    assertThat(eventCount(room, "lot_opened")).isZero();
    assertThat(roomState(room)).isEqualTo("auction_scheduled");
    JsonNode lobby = state(room, room.manager(0));
    assertThat(lobby.get("phase").asText()).isEqualTo("scheduled");
    assertThat(lobby.get("startRefusal").asText()).contains("1 player");
    assertThat(lobby.has("lot")).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT start_refused_at IS NOT NULL FROM auctions WHERE room_id = ?",
                Boolean.class,
                UUID.fromString(room.id())))
        .isTrue();
  }

  @Test
  void tooFewFranchisesAtTheStartInstantAreRefusedToo() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(60));
    schedule(room, T0);
    jdbc.update("DELETE FROM franchises WHERE id = ?", room.franchise(1));

    clock.freeze(T0);
    tick();

    assertThat(eventCount(room, "start_refused")).isEqualTo(1);
    assertThat(state(room, room.commissioner()).get("startRefusal").asText())
        .contains("at least 2 franchises");
    assertThat(roomState(room)).isEqualTo("auction_scheduled");
  }

  @Test
  void afterTheFixAndARescheduleTheAuctionGoesLive() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(60));
    schedule(room, T0);
    replacePool(room.id(), 1, 20);
    clock.freeze(T0);
    tick();
    assertThat(eventCount(room, "start_refused")).isEqualTo(1);

    replacePool(room.id(), 6, 20);
    var rescheduled = schedule(room, T0.plusSeconds(120));
    assertThat(rescheduled.getStatusCode())
        .as(rescheduled.getBody())
        .isEqualTo(org.springframework.http.HttpStatus.OK);
    assertThat(body(rescheduled).has("startRefusal")).isFalse();
    advance(119);
    tick();
    assertThat(state(room, room.manager(0)).get("phase").asText()).isEqualTo("scheduled");
    advance(1);
    tick();

    JsonNode live = state(room, room.manager(0));
    assertThat(live.get("phase").asText()).isEqualTo("live");
    assertThat(live.get("lot").get("no").asInt()).isEqualTo(1);
    assertThat(eventCount(room, "start_refused")).isEqualTo(1);
    assertThat(roomState(room)).isEqualTo("auction_live");
    assertThat(Instant.parse(live.get("startsAt").asText())).isEqualTo(T0.plusSeconds(120));
  }
}
