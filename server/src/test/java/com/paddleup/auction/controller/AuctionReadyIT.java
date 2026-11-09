package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import com.paddleup.auction.StompProbe;
import com.paddleup.auction.service.Readiness;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

/**
 * AC-2 (auction screen): lobby readiness is server state, set only by a franchise while scheduled.
 */
class AuctionReadyIT extends AbstractAuctionIT {

  private static final String ORIGIN = "http://localhost:3000";

  @LocalServerPort int port;
  @Autowired Readiness readiness;

  private void scheduled(Room room) {
    clock.freeze(T0.minusSeconds(60));
    assertThat(schedule(room, T0).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private org.springframework.http.ResponseEntity<String> ready(Room room, Person who, boolean on) {
    return put(who, auction(room, "/ready"), Map.of("ready", on));
  }

  @Test
  void aManagerMarksReadyAndOthersSeeItInStateThenUndoesIt() {
    Room room = room(2, 6);
    scheduled(room);

    var first = ready(room, room.manager(0), true);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(body(first).get("ready").get(0).asText()).isEqualTo(room.franchise(0).toString());
    JsonNode seen = state(room, room.manager(1));
    assertThat(seen.get("phase").asText()).isEqualTo("scheduled");
    assertThat(seen.get("ready")).hasSize(1);
    assertThat(seen.get("ready").get(0).asText()).isEqualTo(room.franchise(0).toString());

    ready(room, room.manager(1), true);
    assertThat(state(room, room.manager(0)).get("ready")).hasSize(2);

    var undone = ready(room, room.manager(0), false);

    assertThat(body(undone).get("ready")).hasSize(1);
    assertThat(state(room, room.manager(1)).get("ready").get(0).asText())
        .isEqualTo(room.franchise(1).toString());
  }

  @Test
  void aCommissionerWithoutAFranchiseIsRefusedWithNotAFranchise() {
    Room room = room(2, 6);
    scheduled(room);

    var refused = ready(room, room.commissioner(), true);

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(refused).get("error").asText()).isEqualTo("not_a_franchise");
    assertThat(state(room, room.manager(0)).get("ready")).isEmpty();
  }

  @Test
  void readyIsRefusedBeforeTheAuctionIsScheduledAndAfterItStarts() {
    Room room = room(2, 6);

    var early = ready(room, room.manager(0), true);
    assertThat(early.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(early).get("error").asText()).isEqualTo("not_scheduled");

    scheduled(room);
    ready(room, room.manager(0), true);
    clock.freeze(T0);
    tick();

    var late = ready(room, room.manager(1), true);
    assertThat(late.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(late).get("error").asText()).isEqualTo("not_scheduled");
    assertThat(readiness.ready(UUID.fromString(room.id()))).isEmpty();
    assertThat(state(room, room.manager(0)).get("ready")).isEmpty();
  }

  @Test
  void aNonMemberAndABodyWithoutReadyAreRefused() {
    Room room = room(2, 6);
    scheduled(room);

    assertThat(ready(room, person(), true).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    var bad = put(room.manager(0), auction(room, "/ready"), Map.of());
    assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void aTicketAndSubscribeWorkWhileScheduledAndReadyIsBroadcastOnPresence() throws Exception {
    Room room = room(2, 6);
    scheduled(room);
    var ticket = post(room.manager(1), auction(room, "/ticket"), null);
    assertThat(ticket.getStatusCode().is2xxSuccessful()).as(ticket.getBody()).isTrue();

    try (StompProbe probe =
        StompProbe.connect(json, port, null, body(ticket).get("ticket").asText(), ORIGIN)) {
      assertThat(probe.connected()).isTrue();
      probe.subscribe("/topic/auctions/" + room.id());
      probe.subscribe("/topic/auctions/" + room.id() + "/presence");
      assertThat(probe.nextError(300)).isNull();
      Thread.sleep(300);
      probe.received.clear();

      ready(room, room.manager(0), true);

      JsonNode presence = null;
      for (int i = 0; i < 10 && presence == null; i++) {
        JsonNode message = probe.next(1000);
        if (message != null && message.has("ready") && message.get("ready").size() == 1) {
          presence = message;
        }
      }
      assertThat(presence).as("a presence message with the ready franchise").isNotNull();
      assertThat(presence.get("ready").get(0).asText()).isEqualTo(room.franchise(0).toString());
      assertThat(presence.get("connected").get(0).asText()).isEqualTo(room.franchise(1).toString());
    }
  }

  @Test
  void settingReadyToItsCurrentValueAnswersTheSameButBroadcastsNothing() throws Exception {
    Room room = room(2, 6);
    scheduled(room);
    var ticket = post(room.manager(1), auction(room, "/ticket"), null);

    try (StompProbe probe =
        StompProbe.connect(json, port, null, body(ticket).get("ticket").asText(), ORIGIN)) {
      probe.subscribe("/topic/auctions/" + room.id() + "/presence");
      Thread.sleep(300);
      probe.received.clear();

      var first = ready(room, room.manager(0), true);
      assertThat(probe.next(1000)).as("first change is broadcast").isNotNull();
      probe.received.clear();

      var again = ready(room, room.manager(0), true);
      var undoNoop = ready(room, room.manager(1), false);

      assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(body(again)).isEqualTo(body(first));
      assertThat(undoNoop.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(probe.next(500)).as("no broadcast for a no-op").isNull();

      ready(room, room.manager(0), false);
      assertThat(probe.next(1000)).as("a real change is broadcast again").isNotNull();
    }
  }
}
