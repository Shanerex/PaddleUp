package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-1, AC-5: the state tells each manager why their own bid is locked; the server refuses it. */
class AuctionBidLockIT extends AbstractAuctionIT {

  private Room roomWithSquadMax(int squadMax) {
    Room room = room(2, 6);
    ObjectNode config =
        (ObjectNode) body(get(room.commissioner(), "/api/rooms/" + room.id())).get("config");
    config.put("squadMax", squadMax);
    var saved =
        put(
            room.commissioner(),
            "/api/rooms/" + room.id() + "/config",
            Map.of("version", configVersion(room.commissioner(), room.id()), "config", config));
    assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
    return room;
  }

  @Test
  void aFranchiseAtSquadMaxIsLockedOutAndOnlyThatFranchise() {
    Room room = roomWithSquadMax(1);
    goLive(room);
    assertThat(lock(state(room, room.manager(0)))).isNull();
    bid(room, room.manager(0), 1, 20);
    advance(10);
    tick();
    advance(5);
    tick();

    JsonNode full = state(room, room.manager(0));
    JsonNode other = state(room, room.manager(1));
    JsonNode commissioner = state(room, room.commissioner());

    assertThat(full.get("lot").get("no").asInt()).isEqualTo(2);
    assertThat(lock(full).get("reason").asText()).isEqualTo("squad_full");
    assertThat(lock(full).has("role")).isFalse();
    assertThat(lock(other)).isNull();
    assertThat(commissioner.get("bidLimits")).isEmpty();
    assertThat(outcome(bid(room, room.manager(0), 2, 20))).isEqualTo("rejected:squad_full");
    assertThat(outcome(bid(room, room.manager(1), 2, 20))).isEqualTo("accepted");
  }

  @Test
  void theLeaderGetsItsLimitsAheadOfBeingOutbidAndAPasserGetsNone() {
    Room room = roomWithSquadMax(1);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    advance(10);
    tick();
    advance(5);
    tick();
    bid(room, room.manager(1), 2, 20);

    // Lot 2: manager 1 leads, and still has its purse limit, so once outbid the dock knows at once.
    JsonNode leader = state(room, room.manager(1));
    assertThat(leader.get("bidLimits")).extracting(l -> l.get("reason").asText()).contains("purse");
    assertThat(lock(leader)).isNull();
    assertThat(lock(state(room, room.manager(0))).get("reason").asText()).isEqualTo("squad_full");
    pass(room, room.manager(0), 2);
    assertThat(state(room, room.manager(0)).get("bidLimits")).isEmpty();
  }

  @Test
  void aBidAboveThePurseIsShownAsAPurseLimitNotASquadOne() {
    Room room = roomWithSquadMax(1);
    goLive(room);

    JsonNode limits = state(room, room.manager(0)).get("bidLimits");
    JsonNode purse = limits.get(0);
    assertThat(purse.get("reason").asText()).isEqualTo("purse");
    assertThat(purse.has("above")).isTrue();
  }
}
