package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.packs.dto.SeasonDatasetDto;
import com.paddleup.rooms.AbstractRoomsIT;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** AC-6, AC-8, AC-9: the Commissioner adds, edits and removes one player at a time. */
class PoolEditIT extends AbstractRoomsIT {

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

  private JsonNode read(Person who) {
    return body(get(who, pool()));
  }

  private static Map<String, Object> player(int version, String name, String role, String set) {
    var m = new LinkedHashMap<String, Object>();
    m.put("version", version);
    m.put("name", name);
    m.put("country", "Chile");
    m.put("role", role);
    m.put("tags", List.of("capped"));
    m.put("basePrice", 20);
    m.put("set", set);
    return m;
  }

  private JsonNode byName(JsonNode pool, String name) {
    for (JsonNode p : pool.get("players")) {
      if (p.get("name").asText().equals(name)) return p;
    }
    return null;
  }

  private ResponseEntity<String> add(Map<String, Object> body) {
    return post(commissioner, pool() + "/players", body);
  }

  @Test
  void addEditAndRemovePersistOnReload() {
    JsonNode before = read(commissioner);
    int v = before.get("version").asInt();

    var added = add(player(v, "New Face", "WK", "Marquee"));
    assertThat(added.getStatusCode()).as(added.getBody()).isEqualTo(HttpStatus.CREATED);
    JsonNode afterAdd = read(commissioner);
    assertThat(afterAdd.get("version").asInt()).isEqualTo(v + 1);
    assertThat(afterAdd.get("players")).hasSize(7);
    JsonNode face = byName(afterAdd, "New Face");
    assertThat(face.get("origin").asText()).isEqualTo("added");
    assertThat(afterAdd.get("editCount").asInt()).isEqualTo(1);
    assertThat(afterAdd.get("sets").get(0).get("players").asInt()).isEqualTo(3);

    String id = face.get("id").asText();
    var edit =
        put(commissioner, pool() + "/players/" + id, player(v + 1, "Renamed", "WK", "Uncapped"));
    assertThat(edit.getStatusCode()).as(edit.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode afterEdit = read(commissioner);
    assertThat(byName(afterEdit, "Renamed").get("set").asText()).isEqualTo("Uncapped");
    assertThat(byName(afterEdit, "Renamed").get("origin").asText()).isEqualTo("added");
    assertThat(byName(afterEdit, "New Face")).isNull();

    var removed = delete(commissioner, pool() + "/players/" + id + "?version=" + (v + 2));
    assertThat(removed.getStatusCode()).as(removed.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode afterRemove = read(commissioner);
    assertThat(afterRemove.get("players")).hasSize(6);
    assertThat(afterRemove.get("version").asInt()).isEqualTo(v + 3);
    assertThat(afterRemove.get("editCount").asInt()).isZero();
  }

  @Test
  void editingACopiedPlayerFlagsItEditedAndEditingItBackClearsTheFlag() {
    JsonNode pool = read(commissioner);
    JsonNode ann = byName(pool, "Ann Lee");
    String id = ann.get("id").asText();
    int v = pool.get("version").asInt();
    var changed = new LinkedHashMap<>(player(v, "Ann Lee", "BAT", "Marquee"));
    changed.put("tags", List.of("capped", "overseas"));
    changed.put("providerId", "p-1");
    changed.put("basePrice", 25);

    assertThat(put(commissioner, pool() + "/players/" + id, changed).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    JsonNode edited = read(commissioner);
    assertThat(byName(edited, "Ann Lee").get("origin").asText()).isEqualTo("edited");
    assertThat(edited.get("editCount").asInt()).isEqualTo(1);

    changed.put("version", v + 1);
    changed.put("basePrice", 20);
    assertThat(put(commissioner, pool() + "/players/" + id, changed).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    JsonNode back = read(commissioner);
    assertThat(byName(back, "Ann Lee").get("origin").asText()).isEqualTo("source");
    assertThat(back.get("editCount").asInt()).isZero();
  }

  @Test
  void removingACopiedPlayerCountsAsAnEdit() {
    JsonNode pool = read(commissioner);
    String id = byName(pool, "Bo Ray").get("id").asText();

    var response =
        delete(commissioner, pool() + "/players/" + id + "?version=" + pool.get("version").asInt());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode after = read(commissioner);
    assertThat(after.get("players")).hasSize(5);
    assertThat(after.get("editCount").asInt()).isEqualTo(1);
  }

  @Test
  void anUnknownRoleOrTagIs422WithAFieldErrorAndNothingChanges() {
    JsonNode before = read(commissioner);
    int v = before.get("version").asInt();
    var badRole = add(player(v, "Zed", "STRIKER", "Marquee"));
    var badTag = new LinkedHashMap<>(player(v, "Zed", "BAT", "Marquee"));
    badTag.put("tags", List.of("mystery"));

    var tagResponse = add(badTag);

    assertThat(badRole.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(body(badRole).get("error").asText()).isEqualTo("invalid_player");
    assertThat(body(badRole).get("violations").findValuesAsText("field")).containsExactly("role");
    assertThat(tagResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(body(tagResponse).get("violations").findValuesAsText("field"))
        .containsExactly("tags");
    assertThat(read(commissioner)).isEqualTo(before);
  }

  @Test
  void anEditWithABadFieldChangesNothing() {
    JsonNode before = read(commissioner);
    String id = byName(before, "Ann Lee").get("id").asText();
    var bad = player(before.get("version").asInt(), "Ann Lee", "NOPE", "Marquee");

    var response = put(commissioner, pool() + "/players/" + id, bad);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(read(commissioner)).isEqualTo(before);
  }

  @Test
  void anUnknownSetIs422AndAnOffLadderOrOverPurseOrZeroPriceIsRejectedWithRoomRules() {
    JsonNode before = read(commissioner);
    int v = before.get("version").asInt();

    var unknownSet = add(player(v, "Zed", "BAT", "No Such Set"));
    var offLadder = new LinkedHashMap<>(player(v, "Zed", "BAT", "Marquee"));
    offLadder.put("basePrice", 23);
    var overPurse = new LinkedHashMap<>(player(v, "Zed", "BAT", "Marquee"));
    overPurse.put("basePrice", 12025);
    var zero = new LinkedHashMap<>(player(v, "Zed", "BAT", "Marquee"));
    zero.put("basePrice", 0);
    var missing = new LinkedHashMap<>(player(v, "Zed", "BAT", "Marquee"));
    missing.remove("basePrice");

    assertThat(unknownSet.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(body(unknownSet).get("violations").findValuesAsText("field")).containsExactly("set");
    for (var bad : List.of(offLadder, overPurse)) {
      var r = add(bad);
      assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
      assertThat(body(r).get("violations").get(0).get("field").asText()).isEqualTo("base_price");
      assertThat(body(r).get("violations").get(0).get("message").asText())
          .startsWith("That base price isn't allowed in ");
    }
    assertThat(add(zero).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(add(missing).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(read(commissioner)).isEqualTo(before);
  }

  @Test
  void aRoomsOwnLadderDecidesWhichPricesItAccepts() {
    UUID id = UUID.fromString(roomId);
    jdbc.update(
        "UPDATE rooms SET config = jsonb_set(config, '{incrementLadder}',"
            + " '[{\"from\":0,\"step\":10}]'::jsonb) WHERE id = ?",
        id);
    int v = read(commissioner).get("version").asInt();

    var five = new LinkedHashMap<>(player(v, "Five", "BAT", "Marquee"));
    five.put("basePrice", 5);
    var ten = new LinkedHashMap<>(player(v, "Ten", "BAT", "Marquee"));
    ten.put("basePrice", 10);

    assertThat(add(five).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(add(ten).getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void removingASetsLastPlayerKeepsTheSetInTheOrderWithZeroPlayers() {
    JsonNode pool = read(commissioner);
    int v = pool.get("version").asInt();
    // "Uncapped" holds Ed Fu and Gus Ho.
    for (String name : List.of("Ed Fu", "Gus Ho")) {
      String id = byName(read(commissioner), name).get("id").asText();
      int current = read(commissioner).get("version").asInt();
      assertThat(
              delete(commissioner, pool() + "/players/" + id + "?version=" + current)
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
    }

    JsonNode after = read(commissioner);

    assertThat(after.get("sets").findValuesAsText("name"))
        .isEqualTo(pool.get("sets").findValuesAsText("name"));
    assertThat(after.get("sets").get(2).get("name").asText()).isEqualTo("Uncapped");
    assertThat(after.get("sets").get(2).get("players").asInt()).isZero();
    assertThat(after.get("version").asInt()).isEqualTo(v + 2);
    var toEmptySet = add(player(after.get("version").asInt(), "Refill", "BAT", "Uncapped"));
    assertThat(toEmptySet.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void editsNeverTouchTheDataset() {
    SeasonDatasetDto before = seasons.latest("cricket-t20").orElseThrow();
    JsonNode pool = read(commissioner);
    String id = byName(pool, "Ann Lee").get("id").asText();
    int v = pool.get("version").asInt();
    put(commissioner, pool() + "/players/" + id, player(v, "Changed", "BAT", "Marquee"));
    add(player(v + 1, "Extra", "BAT", "Marquee"));

    assertThat(seasons.latest("cricket-t20").orElseThrow()).isEqualTo(before);
  }

  @Test
  void aMemberWriteGets403AndAStrangerGets404AndNothingChanges() {
    Person manager = insertManager(roomId, "sky");
    JsonNode before = read(commissioner);
    int v = before.get("version").asInt();
    String id = byName(before, "Ann Lee").get("id").asText();

    assertThat(
            post(manager, pool() + "/players", player(v, "Zed", "BAT", "Marquee")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            put(manager, pool() + "/players/" + id, player(v, "Zed", "BAT", "Marquee"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(delete(manager, pool() + "/players/" + id + "?version=" + v).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            post(person(), pool() + "/players", player(v, "Zed", "BAT", "Marquee")).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(read(commissioner)).isEqualTo(before);
  }

  @Test
  void anUnknownPlayerIs404AndTextIsSanitised() {
    int v = read(commissioner).get("version").asInt();

    var missing =
        put(
            commissioner,
            pool() + "/players/" + UUID.randomUUID(),
            player(v, "Zed", "BAT", "Marquee"));
    var dirty = add(player(v, "<b>Clean</b> Me", "BAT", "Marquee"));

    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(dirty.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(byName(read(commissioner), "Clean Me")).isNotNull();
  }

  @Test
  void aPoolAtTheCapRefusesAnotherAdd() {
    UUID id = UUID.fromString(roomId);
    jdbc.update(
        "INSERT INTO room_pool_players (id, room_id, origin, name, country, role_key, tag_keys,"
            + " base_price, set_name, position)"
            + " SELECT gen_random_uuid(), ?, 'added', 'Bulk ' || g, 'C', 'BAT', ARRAY[]::text[], 20,"
            + " 'Marquee', 1000 + g FROM generate_series(1, 994) g",
        id);
    int v = read(commissioner).get("version").asInt();

    var response = add(player(v, "One Too Many", "BAT", "Marquee"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(body(response).get("violations").findValuesAsText("field")).containsExactly("pool");
  }

  @Test
  void everyWriteIsLogged() {
    JsonNode pool = read(commissioner);
    int v = pool.get("version").asInt();
    add(player(v, "Logged", "BAT", "Marquee"));
    String id = byName(read(commissioner), "Logged").get("id").asText();
    put(commissioner, pool() + "/players/" + id, player(v + 1, "Logged 2", "BAT", "Marquee"));
    delete(commissioner, pool() + "/players/" + id + "?version=" + (v + 2));

    List<String> kinds =
        jdbc.queryForList(
            "SELECT kind FROM room_log WHERE room_id = ? AND kind LIKE 'pool%' ORDER BY id",
            String.class, UUID.fromString(roomId));

    assertThat(kinds)
        .containsExactly(
            "pool_copied", "pool_player_added", "pool_player_edited", "pool_player_removed");
  }
}
