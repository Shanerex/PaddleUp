package com.paddleup.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.PaddleUpApplication;
import com.paddleup.auction.AbstractAuctionIT;
import com.paddleup.support.MutableClock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * AC-14, AC-17: a second application context on the same database is the restarted server. The
 * first context is left idle (its ticker is off), so only the new one writes.
 */
class AuctionRecoveryIT extends AbstractAuctionIT {

  @Autowired Environment env;

  /** A restarted server: its own context, port and clock, sharing the database. */
  private final class Server implements AutoCloseable {
    final ConfigurableApplicationContext ctx;
    final int port;
    final MutableClock clock = new MutableClock();
    final TestRestTemplate http;

    Server(Instant now) {
      clock.freeze(now);
      ctx =
          new SpringApplicationBuilder(PaddleUpApplication.class)
              .initializers(
                  (org.springframework.context.ApplicationContextInitializer<
                          org.springframework.context.support.GenericApplicationContext>)
                      context ->
                          context.registerBean(
                              "restartedClock",
                              MutableClock.class,
                              () -> clock,
                              bd -> bd.setPrimary(true)))
              .run(
                  "--server.port=0",
                  "--spring.datasource.url=" + env.getProperty("spring.datasource.url"),
                  "--spring.datasource.username=" + env.getProperty("spring.datasource.username"),
                  "--spring.datasource.password=" + env.getProperty("spring.datasource.password"),
                  "--paddleup.auction.tick=0",
                  "--paddleup.auction.recovery=true",
                  "--spring.main.allow-bean-definition-overriding=true",
                  "--logging.level.root=WARN");
      port = ((ServletWebServerApplicationContext) ctx).getWebServer().getPort();
      http = new TestRestTemplate();
    }

    JsonNode state(Room room, Person who) {
      var response = call(HttpMethod.GET, room, "", who, null);
      return body(response);
    }

    ResponseEntity<String> bid(Room room, Person who, UUID intent, int lot, long amount) {
      return call(
          HttpMethod.POST,
          room,
          "/bids",
          who,
          Map.of("intentId", intent.toString(), "lot", lot, "amount", amount));
    }

    ResponseEntity<String> schedule(Room room, Instant startsAt) {
      return call(
          HttpMethod.PUT,
          room,
          "/schedule",
          room.commissioner(),
          Map.of("startsAt", startsAt.toString()));
    }

    void tick() {
      ctx.getBean(AuctionTicker.class).tickAll();
    }

    private ResponseEntity<String> call(
        HttpMethod method, Room room, String path, Person who, Object payload) {
      var headers = new HttpHeaders();
      headers.add(HttpHeaders.COOKIE, who.cookie());
      headers.setContentType(MediaType.APPLICATION_JSON);
      return http.exchange(
          "http://localhost:" + port + "/api/rooms/" + room.id() + "/auction" + path,
          method,
          new HttpEntity<>(payload, headers),
          String.class);
    }

    @Override
    public void close() {
      ctx.close();
    }
  }

  private JsonNode withoutNow(JsonNode state) {
    var copy = state.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) copy).remove("serverNow");
    return copy;
  }

  @Test
  void aServerKilledMidLotComesBackWithTheSameLotPriceLeaderPursesAndSeq() {
    Room room = room(3, 6);
    goLive(room);
    UUID intent = UUID.randomUUID();
    bid(room, room.manager(0), intent, 1, 20);
    bid(room, room.manager(1), 1, 25);
    bid(room, room.manager(0), 1, 30);
    pass(room, room.manager(2), 1);
    advance(3);
    JsonNode before = state(room, room.commissioner());
    long seq = lastSeq(room);

    try (Server restarted = new Server(clock.instant())) {
      JsonNode after = restarted.state(room, room.commissioner());

      assertThat(withoutNow(after)).isEqualTo(withoutNow(before));
      assertThat(lastSeq(room)).isEqualTo(seq);
      assertThat(eventCount(room, "lot_resumed")).isZero();

      // The pre-restart intent still answers with its first outcome, and bidding goes on at seq +
      // 1.
      var resent = restarted.bid(room, room.manager(0), intent, 1, 20);
      assertThat(resent.getStatusCode().is2xxSuccessful()).as(resent.getBody()).isTrue();
      assertThat(body(resent).get("outcome").asText()).isEqualTo("accepted");
      assertThat(body(resent).get("seq").asLong()).isEqualTo(4);
      assertThat(lastSeq(room)).isEqualTo(seq);
      var next = restarted.bid(room, room.manager(1), UUID.randomUUID(), 1, 35);
      assertThat(body(next).get("seq").asLong()).isEqualTo(seq + 1);
    }
  }

  @Test
  void aRejectedIntentKeepsItsFirstAnswerAcrossARestartEvenWhenItWouldNowBeAccepted() {
    Room room = room(2, 6);
    goLive(room);
    UUID early = UUID.randomUUID();
    // Rejected: the first bid must be the base price, 20.
    assertThat(outcome(bid(room, room.manager(1), early, 1, 25))).isEqualTo("rejected:outbid");
    bid(room, room.manager(0), 1, 20);
    long seq = lastSeq(room);

    try (Server restarted = new Server(clock.instant())) {
      // 25 is the right amount now, so a fresh decision would accept it.
      var resent = restarted.bid(room, room.manager(1), early, 1, 25);

      assertThat(outcome(resent)).isEqualTo("rejected:outbid");
      assertThat(lastSeq(room)).isEqualTo(seq);
    }
  }

  @Test
  void aRoomWhoseLogCannotBeReplayedDoesNotStopTheServerOrTheOtherRooms() {
    Room broken = room(2, 6);
    goLive(broken);
    Room healthy = room(2, 6);
    goLive(healthy);
    bid(healthy, healthy.manager(0), 1, 20);
    // Damage the broken room: a lot card its log refers to is gone (the tables are append-only,
    // so the guard trigger is lifted for this one statement).
    jdbc.execute("ALTER TABLE auction_lots DISABLE TRIGGER auction_lots_no_update_delete");
    try {
      jdbc.update(
          "DELETE FROM auction_lots WHERE room_id = ? AND lot_no = 1",
          UUID.fromString(broken.id()));
    } finally {
      jdbc.execute("ALTER TABLE auction_lots ENABLE TRIGGER auction_lots_no_update_delete");
    }

    try (Server restarted = new Server(clock.instant())) {
      JsonNode state = restarted.state(healthy, healthy.commissioner());

      assertThat(state.get("phase").asText()).isEqualTo("live");
      assertThat(state.get("currentBid").asLong()).isEqualTo(20);
    } finally {
      // Keep the damaged room out of every later restart in this shared database.
      jdbc.update(
          "UPDATE auctions SET phase = 'ended' WHERE room_id = ?", UUID.fromString(broken.id()));
    }
  }

  @Test
  void aDeadlinePassedInDowntimeLogsAResumeAndGivesAFreshTBidOnce() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    JsonNode before = state(room, room.commissioner());
    long seq = lastSeq(room);
    Instant later = T0.plusSeconds(500);

    try (Server restarted = new Server(later)) {
      JsonNode after = restarted.state(room, room.commissioner());

      assertThat(eventCount(room, "lot_resumed")).isEqualTo(1);
      assertThat(lastSeq(room)).isEqualTo(seq + 1);
      assertThat(after.get("deadline").asText()).isEqualTo(later.plusSeconds(10).toString());
      assertThat(after.get("currentBid").asLong()).isEqualTo(20);
      assertThat(after.get("leaderFranchiseId")).isEqualTo(before.get("leaderFranchiseId"));
      assertThat(after.get("lotOpen").asBoolean()).isTrue();
      assertThat(eventCount(room, "lot_sold")).isZero();
    }
    // A second restart a moment later finds the lot within its fresh deadline: nothing new.
    try (Server again = new Server(later.plusSeconds(2))) {
      assertThat(again.state(room, room.commissioner()).get("lastSeq").asLong()).isEqualTo(seq + 1);
      assertThat(eventCount(room, "lot_resumed")).isEqualTo(1);
    }
  }

  @Test
  void aLateFirstRequestAfterBootResumesTheLotBeforeItIsJudged() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);

    try (Server restarted = new Server(T0.plusSeconds(500))) {
      var late = restarted.bid(room, room.manager(1), UUID.randomUUID(), 1, 25);

      assertThat(body(late).get("outcome").asText()).isEqualTo("accepted");
      assertThat(eventCount(room, "lot_resumed")).isEqualTo(1);
      assertThat(eventCount(room, "lot_sold")).isZero();
    }
  }

  @Test
  void theLobbyAndTheGapResume() {
    Room lobby = room(2, 6);
    clock.freeze(T0.minusSeconds(600));
    schedule(lobby, T0.plusSeconds(13));
    Room gap = room(2, 6);
    goLive(gap);
    bid(gap, gap.manager(0), 1, 20);
    advance(10);
    tick();
    JsonNode inGap = state(gap, gap.commissioner());
    assertThat(inGap.get("lotOpen").asBoolean()).isFalse();

    try (Server restarted = new Server(T0.plusSeconds(11))) {
      // Lobby: kept, start not yet reached.
      JsonNode lobbyState = restarted.state(lobby, lobby.commissioner());
      assertThat(lobbyState.get("phase").asText()).isEqualTo("scheduled");
      assertThat(lobbyState.get("startsAt").asText()).isEqualTo(T0.plusSeconds(13).toString());
      // Gap: continues, the next lot has not opened yet.
      assertThat(withoutNow(restarted.state(gap, gap.commissioner()))).isEqualTo(withoutNow(inGap));

      restarted.clock.freeze(T0.plusSeconds(15));
      restarted.tick();
      JsonNode opened = restarted.state(gap, gap.commissioner());
      assertThat(opened.get("lot").get("no").asInt()).isEqualTo(2);
      assertThat(opened.get("lotOpen").asBoolean()).isTrue();
      assertThat(opened.get("lastResult").get("amount").asLong()).isEqualTo(20);
      // The lobby goes live at its start instant on the new server.
      restarted.clock.freeze(T0.plusSeconds(15));
      restarted.tick();
      assertThat(restarted.state(lobby, lobby.commissioner()).get("phase").asText())
          .isEqualTo("live");
    }
  }

  @Test
  void aStartPassedInDowntimeGoesLive() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(60));
    schedule(room, T0);

    try (Server restarted = new Server(T0.plusSeconds(30))) {
      JsonNode state = restarted.state(room, room.commissioner());

      assertThat(state.get("phase").asText()).isEqualTo("live");
      assertThat(state.get("lot").get("no").asInt()).isEqualTo(1);
      assertThat(state.get("deadline").asText()).isEqualTo(T0.plusSeconds(50).toString());
      assertThat(eventCount(room, "auction_started")).isEqualTo(1);
    }
  }

  @Test
  void aStartPassedInDowntimeIsRefusedOnceAndStaysRefusedAcrossAnotherRestart() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(60));
    schedule(room, T0);
    replacePool(room.id(), 1, 20);

    try (Server first = new Server(T0.plusSeconds(30))) {
      JsonNode state = first.state(room, room.commissioner());
      assertThat(state.get("phase").asText()).isEqualTo("scheduled");
      assertThat(state.get("startRefusal").asText()).contains("1 player");
      assertThat(eventCount(room, "start_refused")).isEqualTo(1);
    }
    try (Server second = new Server(T0.plusSeconds(90))) {
      second.tick();
      JsonNode state = second.state(room, room.commissioner());
      assertThat(state.get("startRefusal").asText()).contains("1 player");
      assertThat(eventCount(room, "start_refused")).isEqualTo(1);
      assertThat(eventCount(room, "auction_started")).isZero();

      // The fix and a reschedule still bring it live.
      replacePool(room.id(), 6, 20);
      assertThat(second.schedule(room, T0.plusSeconds(120)).getStatusCode().is2xxSuccessful())
          .isTrue();
      second.clock.freeze(T0.plusSeconds(120));
      second.tick();
      assertThat(second.state(room, room.commissioner()).get("phase").asText()).isEqualTo("live");
    }
  }
}
