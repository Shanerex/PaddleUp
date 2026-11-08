package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** AC-2: pool, set order and config stay editable while scheduled and lock at go-live. */
class AuctionLockIT extends AbstractAuctionIT {

  private JsonNode pool(Room room) {
    return body(get(room.commissioner(), "/api/rooms/" + room.id() + "/pool"));
  }

  private ResponseEntity<String> reorder(Room room) {
    JsonNode pool = pool(room);
    List<String> order = new java.util.ArrayList<>(pool.get("sets").findValuesAsText("name"));
    java.util.Collections.reverse(order);
    return put(
        room.commissioner(),
        "/api/rooms/" + room.id() + "/pool/sets",
        Map.of("version", pool.get("version").asInt(), "order", order));
  }

  private ResponseEntity<String> removePlayer(Room room) {
    JsonNode pool = pool(room);
    String id = pool.get("players").get(0).get("id").asText();
    return delete(
        room.commissioner(),
        "/api/rooms/"
            + room.id()
            + "/pool/players/"
            + id
            + "?version="
            + pool.get("version").asInt());
  }

  private ResponseEntity<String> saveConfig(Room room, int purse) {
    JsonNode room0 = body(get(room.commissioner(), "/api/rooms/" + room.id()));
    ObjectNode config = (ObjectNode) room0.get("config").deepCopy();
    config.put("purse", purse);
    return put(
        room.commissioner(),
        "/api/rooms/" + room.id() + "/config",
        Map.of("version", room0.get("configVersion").asInt(), "config", config));
  }

  @Test
  void whileScheduledPoolSetOrderConfigAndRescheduleAllSucceed() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(3600));
    schedule(room, T0);

    assertThat(reorder(room).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(removePlayer(room).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(saveConfig(room, 900).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(schedule(room, T0.plusSeconds(60)).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void afterGoLiveEachReturns409AndChangesNothing() {
    Room room = room(2, 6);
    goLive(room);
    UUID id = UUID.fromString(room.id());
    JsonNode poolBefore = pool(room);
    int configVersion = configVersion(room.commissioner(), room.id());
    long seq = lastSeq(room);

    var reorder = reorder(room);
    var remove = removePlayer(room);
    var config = saveConfig(room, 900);
    var reschedule = schedule(room, T0.plusSeconds(600));

    for (var response : List.of(reorder, remove, config, reschedule)) {
      assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CONFLICT);
    }
    assertThat(body(reschedule).get("error").asText()).isEqualTo("locked");
    assertThat(pool(room)).isEqualTo(poolBefore);
    assertThat(configVersion(room.commissioner(), room.id())).isEqualTo(configVersion);
    assertThat(lastSeq(room)).isEqualTo(seq);
    assertThat(eventCount(room, "rescheduled")).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT (config->>'purse')::bigint FROM rooms WHERE id = ?", Long.class, id))
        .isEqualTo(1000);
  }
}
