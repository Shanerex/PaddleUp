package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.rooms.AbstractRoomsIT;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-9, AC-10: reset replaces the copy and set order with the latest dataset. */
class PoolResetIT extends AbstractRoomsIT {

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

  @Test
  void resetDiscardsEditsAndMovesTheCopyToTheLatestVersion() {
    JsonNode first = read(commissioner);
    int v = first.get("version").asInt();
    String id = first.get("players").get(0).get("id").asText();
    delete(commissioner, pool() + "/players/" + id + "?version=" + v);
    put(
        commissioner,
        pool() + "/sets",
        Map.of("version", v + 1, "order", List.of("Uncapped", "Marquee", "Capped Batters")));
    importSeason(
        "cricket-t20",
        "name,country,role,tags,base_price,set\nNewer A,Chile,BAT,,20,Zed\nNewer B,Peru,AR,,50,Alpha\n");
    JsonNode stale = read(commissioner);
    assertThat(stale.get("editCount").asInt()).isEqualTo(1);
    assertThat(stale.get("latestDatasetVersion").asInt())
        .isGreaterThan(stale.get("datasetVersion").asInt());

    var response = post(commissioner, pool() + "/reset", Map.of("version", v + 2));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode after = read(commissioner);
    assertThat(after.get("players").findValuesAsText("name")).containsExactly("Newer A", "Newer B");
    assertThat(after.get("sets").findValuesAsText("name")).containsExactly("Zed", "Alpha");
    assertThat(after.get("datasetVersion").asInt())
        .isEqualTo(after.get("latestDatasetVersion").asInt());
    assertThat(after.get("editCount").asInt()).isZero();
    assertThat(after.get("version").asInt()).isEqualTo(v + 3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM room_log WHERE room_id = ? AND kind = 'pool_reset'",
                Integer.class,
                UUID.fromString(roomId)))
        .isEqualTo(1);
  }

  @Test
  void aRoomWithNoCopyGetsOneOnResetFromVersionZero() {
    UUID id = UUID.fromString(roomId);
    jdbc.update("DELETE FROM room_pool_players WHERE room_id = ?", id);
    jdbc.update("DELETE FROM room_pools WHERE room_id = ?", id);
    assertThat(read(commissioner).get("hasPool").asBoolean()).isFalse();

    var staleTry = post(commissioner, pool() + "/reset", Map.of("version", 3));
    var response = post(commissioner, pool() + "/reset", Map.of("version", 0));

    assertThat(staleTry.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(staleTry).get("error").asText()).isEqualTo("stale_version");
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode after = read(commissioner);
    assertThat(after.get("hasPool").asBoolean()).isTrue();
    assertThat(after.get("players").size()).isGreaterThan(0);
    assertThat(after.get("version").asInt()).isEqualTo(1);
    assertThat(post(commissioner, pool() + "/reset", Map.of("version", 0)).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void aMemberGets403AndNothingChanges() {
    Person manager = insertManager(roomId, "sky");
    JsonNode before = read(commissioner);

    var response =
        post(manager, pool() + "/reset", Map.of("version", before.get("version").asInt()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(read(commissioner)).isEqualTo(before);
  }

  @Test
  void aDatasetThePacksSnapshotCannotDescribeIs409PackMismatchAndNothingChanges() {
    JsonNode before = read(commissioner);
    jdbc.update(
        "UPDATE rooms SET pack_snapshot = jsonb_set(pack_snapshot, '{roles}',"
            + " '[{\"id\":\"BOWL\",\"label\":\"x\"}]'::jsonb) WHERE id = ?",
        UUID.fromString(roomId));

    var response =
        post(commissioner, pool() + "/reset", Map.of("version", before.get("version").asInt()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(response).get("error").asText()).isEqualTo("pack_mismatch");
    assertThat(body(response).get("message").asText()).contains("roles or tags");
    JsonNode after = read(commissioner);
    assertThat(after.get("latestFit").asText()).isEqualTo("pack");
    assertThat(after.get("version")).isEqualTo(before.get("version"));
    assertThat(after.get("players")).isEqualTo(before.get("players"));
  }

  @Test
  void aDatasetWithAPriceTheRoomsPurseCannotAffordIs409AndNotOfferedAsLoadable() {
    JsonNode before = read(commissioner);
    assertThat(before.get("latestFit").asText()).isEqualTo("fits");
    jdbc.update(
        "UPDATE rooms SET config = jsonb_set(config, '{purse}', '60') WHERE id = ?",
        UUID.fromString(roomId));

    JsonNode after = read(commissioner);
    var response =
        post(commissioner, pool() + "/reset", Map.of("version", after.get("version").asInt()));

    assertThat(after.get("latestFit").asText()).isEqualTo("price");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(response).get("error").asText()).isEqualTo("price_mismatch");
    assertThat(body(response).get("message").asText()).contains("purse or price steps");
    assertThat(read(commissioner)).isEqualTo(after);
  }

  @Test
  void aMemberIsNotToldWhetherTheNewestDataFits() {
    Person manager = insertManager(roomId, "sky");

    assertThat(read(manager).get("latestFit").isNull()).isTrue();
    assertThat(read(manager).get("latestDatasetVersion").asInt()).isPositive();
  }

  @Test
  void aRoomCreatedWhenTheLatestDataDoesNotFitGetsNoCopyAndALogRowSaysWhy() {
    // Only a pack that changed after the import can leave data a new room cannot take, so write
    // a newer version with a role the pack lacks straight into the tables.
    UUID admin = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, email, display_name, avatar, created_at, updated_at)"
            + " VALUES (?, ?, 'Admin', 'preset-01', now(), now())",
        admin,
        "admin-" + admin + "@example.test");
    UUID dataset = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO season_datasets (id, pack_id, version, pack_version, source_format,"
            + " imported_by, imported_at, player_count, rejected_count, set_order)"
            + " SELECT ?, pack_id, version + 1, pack_version, 'csv', ?, now(), 1, 0, ARRAY['Zed']"
            + " FROM season_datasets WHERE pack_id = 'cricket-t20' ORDER BY version DESC LIMIT 1",
        dataset,
        admin);
    jdbc.update(
        "INSERT INTO season_players (id, dataset_id, position, name, country, role_key, tag_keys,"
            + " base_price, set_name, provider_player_id)"
            + " VALUES (gen_random_uuid(), ?, 0, 'Ghost', 'Chile', 'GHOST', '{}', 20, 'Zed', NULL)",
        dataset);
    try {
      Person other = person();
      String id = createRoom(other);

      JsonNode pool = body(get(other, "/api/rooms/" + id + "/pool"));
      assertThat(pool.get("hasPool").asBoolean()).isFalse();
      assertThat(pool.get("latestFit").asText()).isEqualTo("pack");
      assertThat(
              jdbc.queryForObject(
                  "SELECT detail->>'reason' FROM room_log WHERE room_id = ?"
                      + " AND kind = 'pool_copy_skipped'",
                  String.class,
                  UUID.fromString(id)))
          .isEqualTo("pack");
    } finally {
      importSeason("cricket-t20", SEASON_CSV); // leave a valid newest version for other tests
    }
  }

  @Test
  void aMissingBodyOrVersionIs400() {
    assertThat(post(commissioner, pool() + "/reset", null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
