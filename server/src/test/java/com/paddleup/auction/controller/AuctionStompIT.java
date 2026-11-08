package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import com.paddleup.auction.StompProbe;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/** AC-12, AC-13, AC-15: the socket carries broadcasts only, behind one auth check. */
class AuctionStompIT extends AbstractAuctionIT {

  private static final String ORIGIN = "http://localhost:3000";

  @LocalServerPort int port;

  private String topic(Room room) {
    return "/topic/auctions/" + room.id();
  }

  private String ticket(Room room, Person who) {
    var response = post(who, auction(room, "/ticket"), null);
    assertThat(response.getStatusCode().is2xxSuccessful()).as(response.getBody()).isTrue();
    return body(response).get("ticket").asText();
  }

  private StompProbe viaCookie(Person who) throws Exception {
    return StompProbe.connect(json, port, who.cookie(), null, ORIGIN);
  }

  private StompProbe viaTicket(String ticket) throws Exception {
    return StompProbe.connect(json, port, null, ticket, ORIGIN);
  }

  private JsonNode awaitKind(StompProbe probe, String kind) throws Exception {
    for (int i = 0; i < 20; i++) {
      JsonNode message = probe.next(1000);
      if (message != null && kind.equals(message.get("kind").asText())) {
        return message;
      }
    }
    throw new AssertionError("no " + kind + " event arrived");
  }

  @Test
  void aMemberWithASessionSubscribesAndReceivesEventsThatCarrySeq() throws Exception {
    Room room = room(2, 6);
    goLive(room);
    try (StompProbe probe = viaCookie(room.manager(0))) {
      assertThat(probe.connected()).isTrue();
      probe.subscribe(topic(room));
      Thread.sleep(300);

      bid(room, room.manager(1), 1, 20);
      bid(room, room.manager(0), 1, 25);

      JsonNode first = awaitKind(probe, "bid_accepted");
      JsonNode second = awaitKind(probe, "bid_accepted");
      assertThat(first.get("seq").asLong()).isEqualTo(4);
      assertThat(second.get("seq").asLong()).isEqualTo(5);
      assertThat(first.get("franchiseId").asText()).isEqualTo(room.franchise(1).toString());
      assertThat(first.get("payload").get("amount").asLong()).isEqualTo(20);
      assertThat(first.get("serverNow").asText()).startsWith("2030-03-01T10:00:00");
      assertThat(first.get("at").asText()).doesNotContain("E");
      assertThat(first.toString()).doesNotContain("intentId");
    }
  }

  @Test
  void aMemberWithATicketAndNoCookieReceivesTheSameEvents() throws Exception {
    Room room = room(2, 6);
    goLive(room);
    String ticket = ticket(room, room.manager(0));
    try (StompProbe probe = viaTicket(ticket)) {
      assertThat(probe.connected()).isTrue();
      probe.subscribe(topic(room));
      Thread.sleep(300);

      bid(room, room.manager(1), 1, 20);

      JsonNode event = awaitKind(probe, "bid_accepted");
      assertThat(event.get("seq").asLong()).isEqualTo(4);
      assertThat(event.get("lot").asInt()).isEqualTo(1);
    }
  }

  @Test
  void aLotOpenedBroadcastCarriesTheCardAndDeadline() throws Exception {
    Room room = room(2, 6);
    goLive(room);
    try (StompProbe probe = viaCookie(room.manager(0))) {
      probe.subscribe(topic(room));
      Thread.sleep(300);

      advance(20);
      tick();
      advance(5);
      tick();

      JsonNode opened = awaitKind(probe, "lot_opened");
      assertThat(opened.get("lot").asInt()).isEqualTo(2);
      assertThat(opened.get("payload").get("card").get("name").asText()).isEqualTo("Player 2");
      assertThat(opened.get("payload").get("deadline").asText())
          .isEqualTo(clock.instant().plusSeconds(20).toString());
    }
  }

  @Test
  void aNonMemberCannotSubscribeOnEitherPath() throws Exception {
    Room room = room(2, 6);
    goLive(room);
    Person outsider = person();
    // A ticket-only client who belongs to a different room.
    Room other = room(2, 6);
    String otherTicket = ticket(other, other.manager(0));

    try (StompProbe byCookie = viaCookie(outsider);
        StompProbe byTicket = viaTicket(otherTicket)) {
      assertThat(byCookie.connected()).isTrue();
      assertThat(byTicket.connected()).isTrue();
      byCookie.subscribe(topic(room));
      byTicket.subscribe(topic(room));

      assertThat(byCookie.nextError(3000)).isNotNull();
      assertThat(byTicket.nextError(3000)).isNotNull();
      bid(room, room.manager(1), 1, 20);
      assertThat(byCookie.next(700)).isNull();
      assertThat(byTicket.next(700)).isNull();
    }
  }

  @Test
  void connectWithNeitherASessionNorAValidTicketIsRefused() throws Exception {
    try (StompProbe none = StompProbe.connect(json, port, null, null, ORIGIN);
        StompProbe junk = StompProbe.connect(json, port, null, "not-a-ticket", ORIGIN)) {
      assertThat(none.connected()).isFalse();
      assertThat(junk.connected()).isFalse();
      assertThat(none.nextError(2000)).isNotNull();
      assertThat(junk.nextError(2000)).isNotNull();
    }
  }

  @Test
  void aReusedTicketAndAnExpiredTicketAreRefused() throws Exception {
    Room room = room(2, 6);
    String used = ticket(room, room.manager(0));
    String stale = ticket(room, room.manager(1));

    try (StompProbe first = viaTicket(used)) {
      assertThat(first.connected()).isTrue();
    }
    try (StompProbe again = viaTicket(used)) {
      assertThat(again.connected()).isFalse();
    }
    clock.freeze(clock.instant());
    advance(31);
    try (StompProbe late = viaTicket(stale)) {
      assertThat(late.connected()).isFalse();
    }
  }

  @Test
  void aTicketIsBoundToItsOwnerAndOnlyOpensThatUsersRooms() throws Exception {
    Room room = room(2, 6);
    goLive(room);
    Room other = room(2, 6);
    String ticket = ticket(other, other.manager(0));

    try (StompProbe probe = viaTicket(ticket)) {
      probe.subscribe(topic(room));
      assertThat(probe.nextError(3000)).isNotNull();
    }
  }

  @Test
  void sendingAFrameIsRefusedAndNothingIsBroadcast() throws Exception {
    Room room = room(2, 6);
    goLive(room);
    try (StompProbe watcher = viaCookie(room.manager(1));
        StompProbe sender = viaCookie(room.manager(0))) {
      watcher.subscribe(topic(room));
      Thread.sleep(300);
      long seq = lastSeq(room);

      sender.send(topic(room), "{\"kind\":\"bid_accepted\",\"seq\":99}");

      assertThat(sender.nextError(3000)).isNotNull();
      assertThat(watcher.next(700)).isNull();
      assertThat(lastSeq(room)).isEqualTo(seq);
    }
  }

  @Test
  void aForeignOriginHandshakeIsRefused() throws Exception {
    try (StompProbe probe = StompProbe.connect(json, port, null, null, "http://evil.example")) {
      assertThat(probe.connected()).isFalse();
    }
  }

  @Test
  void lobbyPresenceShowsTheConnectedFranchises() throws Exception {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(60));
    schedule(room, T0);
    assertThat(state(room, room.commissioner()).get("connected")).isEmpty();

    try (StompProbe first = viaCookie(room.manager(0))) {
      first.subscribe(topic(room));
      awaitConnected(room, 1);
      assertThat(state(room, room.manager(1)).get("connected").get(0).asText())
          .isEqualTo(room.franchise(0).toString());

      try (StompProbe second = viaTicket(ticket(room, room.manager(1)))) {
        second.subscribe(topic(room));
        awaitConnected(room, 2);
      }
      awaitConnected(room, 1);
    }
    awaitConnected(room, 0);
  }

  @Test
  void aCommissionerWithoutAFranchiseCanWatchButIsNotCounted() throws Exception {
    Room room = room(2, 6);
    goLive(room);
    try (StompProbe probe = viaCookie(room.commissioner())) {
      probe.subscribe(topic(room));
      Thread.sleep(300);
      assertThat(state(room, room.commissioner()).get("connected")).isEmpty();
      bid(room, room.manager(0), 1, 20);
      assertThat(awaitKind(probe, "bid_accepted").get("seq").asLong()).isEqualTo(4);
    }
  }

  private void awaitConnected(Room room, int count) throws Exception {
    long end = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    int seen = -1;
    while (System.nanoTime() < end) {
      seen = state(room, room.commissioner()).get("connected").size();
      if (seen == count) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("expected " + count + " connected, saw " + seen);
  }
}
