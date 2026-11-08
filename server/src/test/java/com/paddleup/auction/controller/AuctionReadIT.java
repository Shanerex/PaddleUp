package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-12: members read full state and the log from a given seq; non-members get 404. */
class AuctionReadIT extends AbstractAuctionIT {

  @Test
  void fullStateCarriesLotBidLeaderDeadlinePursesSquadsSeqAndServerTime() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    advance(3);

    JsonNode s = state(room, room.manager(1));

    assertThat(s.get("phase").asText()).isEqualTo("live");
    assertThat(s.get("lot").get("name").asText()).isEqualTo("Player 1");
    assertThat(s.get("currentBid").asLong()).isEqualTo(20);
    assertThat(s.get("leaderFranchiseId").asText()).isEqualTo(room.franchise(0).toString());
    assertThat(Instant.parse(s.get("deadline").asText())).isEqualTo(T0.plusSeconds(10));
    assertThat(Instant.parse(s.get("serverNow").asText())).isEqualTo(T0.plusSeconds(3));
    assertThat(s.get("lastSeq").asLong()).isEqualTo(lastSeq(room));
    assertThat(s.get("standings")).hasSize(2);
    assertThat(s.get("standings").get(0).get("purse").asLong()).isEqualTo(1000);
    assertThat(s.get("standings").get(0).get("squad")).isEmpty();
    assertThat(s.get("config").get("bidSeconds").asInt()).isEqualTo(10);
    assertThat(s.get("config").get("ladder")).hasSize(4);
    assertThat(s.get("connected")).isEmpty();
    assertThat(s.get("lotCount").asInt()).isEqualTo(6);
  }

  @Test
  void aStateBeforeAnyScheduleIs404ForMembersToo() {
    Room room = room(2, 6);

    assertThat(get(room.commissioner(), auction(room, "")).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(body(get(room.commissioner(), auction(room, ""))).get("error").asText())
        .isEqualTo("no_auction");
  }

  @Test
  void theLogFromSeqNReturnsNPlusOneOnward() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    bid(room, room.manager(1), 1, 25);
    pass(room, room.manager(0), 1);
    long last = lastSeq(room);
    assertThat(last).isEqualTo(6);

    JsonNode all = events(room, room.manager(0), 0);
    JsonNode tail = events(room, room.manager(0), 3);
    JsonNode none = events(room, room.manager(0), last);

    assertThat(all.get("events")).hasSize(6);
    assertThat(all.get("lastSeq").asLong()).isEqualTo(last);
    assertThat(all.get("more").asBoolean()).isFalse();
    assertThat(tail.get("events").findValues("seq").stream().map(JsonNode::asLong))
        .containsExactly(4L, 5L, 6L);
    assertThat(tail.get("events").findValuesAsText("kind"))
        .containsExactly("bid_accepted", "bid_accepted", "passed");
    assertThat(none.get("events")).isEmpty();
    assertThat(none.get("lastSeq").asLong()).isEqualTo(last);
  }

  @Test
  void aPageIsCappedAndSaysThereIsMore() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);

    var response = get(room.manager(0), auction(room, "/events?after=0&limit=2"));
    JsonNode page = body(response);

    assertThat(page.get("events")).hasSize(2);
    assertThat(page.get("more").asBoolean()).isTrue();
    assertThat(page.get("lastSeq").asLong()).isEqualTo(2);
    assertThat(get(room.manager(0), auction(room, "/events?after=-1")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void theLogNeverShowsIntentIdsOrUserIds() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);

    String raw = get(room.manager(0), auction(room, "/events")).getBody();

    assertThat(raw).doesNotContain("intentId").doesNotContain("actor");
  }

  @Test
  void aNonMemberGets404OnEveryReadAndAWriteRoute() {
    Room room = room(2, 6);
    goLive(room);
    Person outsider = person();

    assertThat(get(outsider, auction(room, "")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(outsider, auction(room, "/events?after=0")).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(post(outsider, auction(room, "/ticket"), null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(pass(room, outsider, 1).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(null, auction(room, "")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
