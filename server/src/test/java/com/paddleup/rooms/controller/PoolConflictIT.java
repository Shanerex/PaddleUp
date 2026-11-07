package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.accounts.dto.UserId;
import com.paddleup.rooms.AbstractRoomsIT;
import com.paddleup.rooms.dto.RoomState;
import com.paddleup.rooms.service.RoomLifecycle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** AC-11, AC-12: the state x entry-point matrix for pool writes. */
class PoolConflictIT extends AbstractRoomsIT {

  @Autowired RoomLifecycle lifecycle;

  Person commissioner;
  String roomId;

  @BeforeEach
  void room() {
    importSeason("cricket-t20", SEASON_CSV);
    commissioner = person();
    roomId = createRoom(commissioner);
  }

  private String pool() {
    return "/api/rooms/" + roomId + "/pool";
  }

  private JsonNode read() {
    return body(get(commissioner, pool()));
  }

  private static Map<String, Object> player(int version) {
    var m = new LinkedHashMap<String, Object>();
    m.put("version", version);
    m.put("name", "Matrix");
    m.put("country", "Chile");
    m.put("role", "BAT");
    m.put("tags", List.of());
    m.put("basePrice", 20);
    m.put("set", "Marquee");
    return m;
  }

  /** One request per write entry point, each carrying {@code version}. */
  private Map<String, Supplier<ResponseEntity<String>>> writes(int version, String playerId) {
    var calls = new LinkedHashMap<String, Supplier<ResponseEntity<String>>>();
    calls.put("add", () -> post(commissioner, pool() + "/players", player(version)));
    calls.put("edit", () -> put(commissioner, pool() + "/players/" + playerId, player(version)));
    calls.put(
        "remove",
        () -> delete(commissioner, pool() + "/players/" + playerId + "?version=" + version));
    calls.put("reset", () -> post(commissioner, pool() + "/reset", Map.of("version", version)));
    calls.put(
        "sets",
        () ->
            put(
                commissioner,
                pool() + "/sets",
                Map.of(
                    "version",
                    version,
                    "order",
                    List.of("Uncapped", "Marquee", "Capped Batters"))));
    return calls;
  }

  @Test
  void aStaleVersionIs409OnEveryWriteAndChangesNothing() {
    JsonNode first = read();
    int v = first.get("version").asInt();
    String playerId = first.get("players").get(0).get("id").asText();
    // A second tab writes, so the first tab's version is now old.
    assertThat(post(commissioner, pool() + "/players", player(v)).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    JsonNode latest = read();

    for (var call : writes(v, playerId).entrySet()) {
      var response = call.getValue().get();
      assertThat(response.getStatusCode()).as(call.getKey()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(body(response).get("error").asText()).as(call.getKey()).isEqualTo("stale_version");
    }

    assertThat(read()).isEqualTo(latest);
    assertThat(latest.get("version").asInt()).isEqualTo(v + 1);
    assertThat(latest.get("players")).hasSize(7);
  }

  @Test
  void afterTheAuctionGoesLiveEveryWriteIs409RoomLockedAndChangesNothing() {
    JsonNode before = read();
    int v = before.get("version").asInt();
    String playerId = before.get("players").get(0).get("id").asText();
    jdbc.update("UPDATE rooms SET state = 'auction_live' WHERE id = ?", UUID.fromString(roomId));

    for (var call : writes(v, playerId).entrySet()) {
      var response = call.getValue().get();
      assertThat(response.getStatusCode()).as(call.getKey()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(body(response).get("error").asText()).as(call.getKey()).isEqualTo("room_locked");
    }

    JsonNode after = read();
    assertThat(after.get("players")).isEqualTo(before.get("players"));
    assertThat(after.get("version").asInt()).isEqualTo(v);
    assertThat(after.get("canEdit").asBoolean()).isFalse();
  }

  @Test
  void aLockedRoomStillSaysLockedWhenTheVersionIsAlsoStale() {
    JsonNode before = read();
    jdbc.update("UPDATE rooms SET state = 'season_live' WHERE id = ?", UUID.fromString(roomId));

    var response =
        post(commissioner, pool() + "/players", player(before.get("version").asInt() + 7));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(response).get("error").asText()).isEqualTo("room_locked");
  }

  @Test
  void aSecondReadAfterAConflictReturnsTheLatestData() {
    JsonNode first = read();
    int v = first.get("version").asInt();
    post(commissioner, pool() + "/players", player(v));

    var stale = post(commissioner, pool() + "/players", player(v));
    JsonNode second = read();

    assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(second.get("version").asInt()).isEqualTo(v + 1);
    assertThat(second.get("players")).hasSize(first.get("players").size() + 1);
  }

  @Test
  void aWriteRacingTheAuctionStartNeverCommitsAfterTheAuctionIsLive() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 8; round++) {
        Person c = person();
        String id = createRoom(c);
        ensurePool(id);
        insertManager(id, "sky");
        insertManager(id, "teal");
        assertThat(
                post(c, "/api/rooms/" + id + "/transitions", Map.of("to", "auction_scheduled"))
                    .getStatusCode())
            .isEqualTo(HttpStatus.OK);
        int v = body(get(c, "/api/rooms/" + id + "/pool")).get("version").asInt();
        var go = new CountDownLatch(1);
        UserId actor = new UserId(userId(c));

        Future<Object> advance =
            executor.submit(
                () -> {
                  go.await();
                  return lifecycle.advance(UUID.fromString(id), RoomState.AUCTION_LIVE, actor);
                });
        Future<ResponseEntity<String>> write =
            executor.submit(
                () -> {
                  go.await();
                  return post(c, "/api/rooms/" + id + "/pool/players", player(v));
                });
        go.countDown();
        advance.get();
        var response = write.get();

        List<Long> transition =
            jdbc.queryForList(
                "SELECT id FROM room_log WHERE room_id = ? AND kind = 'transition'"
                    + " AND detail->>'to' = 'auction_live'",
                Long.class,
                UUID.fromString(id));
        List<Long> adds =
            jdbc.queryForList(
                "SELECT id FROM room_log WHERE room_id = ? AND kind = 'pool_player_added'",
                Long.class,
                UUID.fromString(id));
        assertThat(transition).hasSize(1);
        if (response.getStatusCode() == HttpStatus.CREATED) {
          assertThat(adds).hasSize(1);
          assertThat(adds.get(0)).isLessThan(transition.get(0));
        } else {
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(body(response).get("error").asText()).isEqualTo("room_locked");
          assertThat(adds).isEmpty();
        }
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM room_pool_players WHERE room_id = ? AND name = 'Matrix'",
                    Integer.class,
                    UUID.fromString(id)))
            .isEqualTo(adds.size());
      }
    } finally {
      executor.shutdownNow();
    }
  }
}
