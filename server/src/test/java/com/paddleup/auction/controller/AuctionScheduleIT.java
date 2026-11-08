package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-1: the Commissioner schedules and reschedules; members see the lobby countdown. */
class AuctionScheduleIT extends AbstractAuctionIT {

  private String roomState(Room room) {
    return jdbc.queryForObject(
        "SELECT state FROM rooms WHERE id = ?", String.class, UUID.fromString(room.id()));
  }

  private int auctionRows(Room room) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM auctions WHERE room_id = ?",
        Integer.class,
        UUID.fromString(room.id()));
  }

  @Test
  void aFutureStartIsAcceptedMovesTheRoomToScheduledAndLogsIt() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(3600));

    var response = schedule(room, T0);

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode state = body(response);
    assertThat(state.get("phase").asText()).isEqualTo("scheduled");
    assertThat(Instant.parse(state.get("startsAt").asText())).isEqualTo(T0);
    assertThat(roomState(room)).isEqualTo("auction_scheduled");
    assertThat(eventCount(room, "scheduled")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM room_log WHERE room_id = ? AND kind = 'transition'"
                    + " AND detail->>'to' = 'auction_scheduled'",
                Integer.class,
                UUID.fromString(room.id())))
        .isEqualTo(1);
  }

  @Test
  void lobbyStateCarriesStartsAtAndServerNowForEveryMember() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(3600));
    schedule(room, T0);

    for (Person who : new Person[] {room.commissioner(), room.manager(0), room.manager(1)}) {
      JsonNode state = state(room, who);
      assertThat(state.get("phase").asText()).isEqualTo("scheduled");
      assertThat(Instant.parse(state.get("startsAt").asText())).isEqualTo(T0);
      assertThat(Instant.parse(state.get("serverNow").asText())).isEqualTo(T0.minusSeconds(3600));
      assertThat(state.get("lastSeq").asLong()).isEqualTo(1);
      assertThat(state.has("lot")).isFalse();
    }
  }

  @Test
  void aPastOrCurrentStartIs400AndNothingIsScheduled() {
    Room room = room(2, 6);
    clock.freeze(T0);

    var past = schedule(room, T0.minusSeconds(1));
    var now = schedule(room, T0);

    assertThat(past.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body(past).get("error").asText()).isEqualTo("invalid_request");
    assertThat(now.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(roomState(room)).isEqualTo("setup");
    assertThat(auctionRows(room)).isZero();
  }

  @Test
  void aMissingStartIs400() {
    Room room = room(2, 6);

    var response = put(room.commissioner(), auction(room, "/schedule"), java.util.Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void aManagerWhoIsNotTheCommissionerGets403AndANonMemberGets404() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(60));

    var manager =
        put(
            room.manager(0),
            auction(room, "/schedule"),
            java.util.Map.of("startsAt", T0.toString()));
    var outsider =
        put(person(), auction(room, "/schedule"), java.util.Map.of("startsAt", T0.toString()));

    assertThat(manager.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(outsider.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(roomState(room)).isEqualTo("setup");
    assertThat(auctionRows(room)).isZero();
  }

  @Test
  void withNoPoolCopyOrATooSmallPoolTheRequestIsRefusedAndTheRoomStaysUnscheduled() {
    Room noPool = room(2, 6);
    jdbc.update("DELETE FROM room_pool_players WHERE room_id = ?", UUID.fromString(noPool.id()));
    jdbc.update("DELETE FROM room_pools WHERE room_id = ?", UUID.fromString(noPool.id()));
    Room small = room(2, 6);
    replacePool(small.id(), 1, 20);
    // 2 franchises x squadMin 1 = 2 players needed; the pool has 1.
    clock.freeze(T0.minusSeconds(60));

    for (Room room : new Room[] {noPool, small}) {
      var response = schedule(room, T0);

      assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(body(response).get("error").asText()).isEqualTo("not_ready");
      assertThat(roomState(room)).isEqualTo("setup");
      assertThat(auctionRows(room)).isZero();
      assertThat(eventCount(room, "scheduled")).isZero();
      assertThat(get(room.commissioner(), auction(room, "")).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Test
  void tooFewFranchisesAreRefusedToo() {
    Room room = room(1, 6);
    clock.freeze(T0.minusSeconds(60));

    var response = schedule(room, T0);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(roomState(room)).isEqualTo("setup");
  }

  @Test
  void aRescheduleBeforeGoLiveReplacesTheStartAndLogsIt() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(3600));
    schedule(room, T0);

    var response = schedule(room, T0.plusSeconds(600));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(Instant.parse(body(response).get("startsAt").asText()))
        .isEqualTo(T0.plusSeconds(600));
    assertThat(roomState(room)).isEqualTo("auction_scheduled");
    assertThat(eventCount(room, "rescheduled")).isEqualTo(1);
    assertThat(eventCount(room, "scheduled")).isEqualTo(1);
    JsonNode log = events(room, room.manager(0), 0);
    assertThat(log.get("events").findValuesAsText("kind"))
        .containsExactly("scheduled", "rescheduled");
    assertThat(log.get("events").get(1).get("payload").get("to").asText())
        .isEqualTo(T0.plusSeconds(600).toString());
  }

  @Test
  void aRoomAlreadyMovedToScheduledOverRestCanStillBeScheduled() {
    Room room = room(2, 6);
    var moved =
        post(
            room.commissioner(),
            "/api/rooms/" + room.id() + "/transitions",
            java.util.Map.of("to", "auction_scheduled"));
    assertThat(moved.getStatusCode()).as(moved.getBody()).isEqualTo(HttpStatus.OK);
    clock.freeze(T0.minusSeconds(60));

    var response = schedule(room, T0);

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(roomState(room)).isEqualTo("auction_scheduled");
  }
}
