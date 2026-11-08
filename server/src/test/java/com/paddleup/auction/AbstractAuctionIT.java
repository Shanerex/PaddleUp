package com.paddleup.auction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.auction.service.AuctionTicker;
import com.paddleup.rooms.AbstractRoomsIT;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Helpers for auction integration tests over real HTTP. The clock is a mutable test clock and the
 * auction ticker is off: a test moves the clock and calls {@link #tick()}.
 */
public abstract class AbstractAuctionIT extends AbstractRoomsIT {

  /** Start of every test auction on the test clock. */
  protected static final Instant T0 = Instant.parse("2030-03-01T10:00:00Z");

  @Autowired protected AuctionTicker ticker;

  /**
   * A room ready to schedule: its Commissioner, managers (one franchise each) and franchise ids.
   */
  public record Room(String id, Person commissioner, List<Person> managers, List<UUID> franchises) {
    public Person manager(int i) {
      return managers.get(i);
    }

    public UUID franchise(int i) {
      return franchises.get(i);
    }
  }

  protected String auction(Room room, String path) {
    return "/api/rooms/" + room.id() + "/auction" + path;
  }

  protected void tick() {
    ticker.tickAll();
  }

  /**
   * A room with {@code managers} franchises (purse 1000, squads of 1 to 5) and a pool of {@code
   * players} players at base price 20 over the three test sets, in dataset order.
   */
  protected Room room(int managers, int players) {
    return room(managers, players, 20, 10, 5);
  }

  protected Room room(int managers, int players, int open, int bid, int gap) {
    ensureSeason();
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    List<Person> people = new ArrayList<>();
    List<UUID> franchises = new ArrayList<>();
    for (int i = 0; i < managers; i++) {
      Person manager = insertManager(roomId, COLOURS.get(i));
      people.add(manager);
      franchises.add(
          jdbc.queryForObject(
              "SELECT id FROM franchises WHERE room_id = ? AND owner_user_id = ?",
              UUID.class,
              UUID.fromString(roomId),
              userId(manager)));
    }
    ObjectNode config = (ObjectNode) body(get(commissioner, "/api/rooms/" + roomId)).get("config");
    config.put("purse", 1000).put("squadMin", 1).put("squadMax", 5);
    config.putObject("roleMinimums");
    config.put("openSeconds", open).put("bidSeconds", bid).put("gapSeconds", gap);
    var saved =
        put(
            commissioner,
            "/api/rooms/" + roomId + "/config",
            Map.of("version", 1, "config", config));
    assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
    replacePool(roomId, players, 20);
    return new Room(roomId, commissioner, people, franchises);
  }

  /** Replaces the room's pool with {@code players} players spread over the dataset's sets. */
  protected void replacePool(String roomId, int players, long basePrice) {
    UUID room = UUID.fromString(roomId);
    jdbc.update("DELETE FROM room_pool_players WHERE room_id = ?", room);
    jdbc.update("DELETE FROM room_pools WHERE room_id = ?", room);
    jdbc.update(
        "INSERT INTO room_pools (room_id, dataset_id, dataset_version, source_count, set_order,"
            + " created_at, updated_at)"
            + " SELECT ?, id, version, 0, set_order, now(), now() FROM season_datasets"
            + " WHERE pack_id = 'cricket-t20' ORDER BY version DESC LIMIT 1",
        room);
    jdbc.update(
        "INSERT INTO room_pool_players (id, room_id, source_player_id, origin, name, country,"
            + " role_key, tag_keys, base_price, set_name, provider_player_id, position)"
            + " SELECT gen_random_uuid(), p.room_id, NULL, 'added', 'Player ' || (n + 1), 'Chile',"
            + " 'BAT', '{}', ?, p.set_order[1 + (n * 3 / ?)], NULL, n"
            + " FROM room_pools p, generate_series(0, ? - 1) n WHERE p.room_id = ?",
        basePrice,
        players,
        players,
        room);
  }

  protected ResponseEntity<String> schedule(Room room, Instant startsAt) {
    return put(
        room.commissioner(), auction(room, "/schedule"), Map.of("startsAt", startsAt.toString()));
  }

  /** Schedules for {@link #T0}, moves the clock there and ticks: lot 1 is open. */
  protected void goLive(Room room) {
    clock.freeze(T0.minusSeconds(60));
    var scheduled = schedule(room, T0);
    assertThat(scheduled.getStatusCode()).as(scheduled.getBody()).isEqualTo(HttpStatus.OK);
    clock.freeze(T0);
    tick();
    assertThat(state(room, room.commissioner()).get("phase").asText()).isEqualTo("live");
  }

  protected ResponseEntity<String> bid(Room room, Person who, int lot, long amount) {
    return bid(room, who, UUID.randomUUID(), lot, amount);
  }

  protected ResponseEntity<String> bid(Room room, Person who, UUID intentId, int lot, long amount) {
    return post(
        who,
        auction(room, "/bids"),
        Map.of("intentId", intentId.toString(), "lot", lot, "amount", amount));
  }

  protected ResponseEntity<String> pass(Room room, Person who, int lot) {
    return post(
        who,
        auction(room, "/passes"),
        Map.of("intentId", UUID.randomUUID().toString(), "lot", lot));
  }

  protected JsonNode state(Room room, Person who) {
    var response = get(who, auction(room, ""));
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    return body(response);
  }

  protected JsonNode events(Room room, Person who, long after) {
    var response = get(who, auction(room, "/events?after=" + after));
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    return body(response);
  }

  protected int eventCount(Room room, String kind) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM auction_events WHERE room_id = ? AND kind = ?",
        Integer.class,
        UUID.fromString(room.id()),
        kind);
  }

  protected long lastSeq(Room room) {
    return jdbc.queryForObject(
        "SELECT COALESCE(max(seq), 0) FROM auction_events WHERE room_id = ?",
        Long.class,
        UUID.fromString(room.id()));
  }

  protected void advance(long seconds) {
    clock.advance(Duration.ofSeconds(seconds));
  }

  protected String outcome(ResponseEntity<String> response) {
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode b = body(response);
    return b.get("outcome").asText() + (b.has("reason") ? ":" + b.get("reason").asText() : "");
  }
}
