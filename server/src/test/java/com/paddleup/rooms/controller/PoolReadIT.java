package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.packs.dto.SeasonPlayerDto;
import com.paddleup.rooms.AbstractRoomsIT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-5, AC-6: a new room copies the latest dataset and every member can read the copy. */
class PoolReadIT extends AbstractRoomsIT {

  private static String pool(String roomId) {
    return "/api/rooms/" + roomId + "/pool";
  }

  private static List<String> names(JsonNode players) {
    return players.findValuesAsText("name");
  }

  @Test
  void aNewRoomsCopyEqualsTheLatestDatasetPlayersAndSetOrder() {
    importSeason("cricket-t20", SEASON_CSV);
    var latest = seasons.latest("cricket-t20").orElseThrow();
    Person commissioner = person();

    String roomId = createRoom(commissioner);
    var response = get(commissioner, pool(roomId));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode pool = body(response);
    assertThat(pool.get("hasPool").asBoolean()).isTrue();
    assertThat(pool.get("version").asInt()).isEqualTo(1);
    assertThat(pool.get("datasetVersion").asInt()).isEqualTo(latest.version());
    assertThat(pool.get("latestDatasetVersion").asInt()).isEqualTo(latest.version());
    assertThat(pool.get("editCount").asInt()).isZero();
    assertThat(pool.get("canEdit").asBoolean()).isTrue();
    assertThat(names(pool.get("players")))
        .containsExactlyElementsOf(latest.players().stream().map(SeasonPlayerDto::name).toList());
    JsonNode first = pool.get("players").get(0);
    SeasonPlayerDto source = latest.players().get(0);
    assertThat(first.get("role").asText()).isEqualTo(source.role());
    assertThat(first.get("basePrice").asLong()).isEqualTo(source.basePrice());
    assertThat(first.get("set").asText()).isEqualTo(source.set());
    assertThat(first.get("origin").asText()).isEqualTo("source");
    assertThat(first.get("tags"))
        .extracting(JsonNode::asText)
        .containsExactlyElementsOf(source.tags());
    assertThat(pool.get("sets").findValuesAsText("name"))
        .containsExactlyElementsOf(latest.setOrder());
    assertThat(pool.get("sets").get(0).get("players").asInt()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM room_log WHERE room_id = ? AND kind = 'pool_copied'",
                Integer.class,
                UUID.fromString(roomId)))
        .isEqualTo(1);
  }

  @Test
  void aRoomMadeBeforeAnotherImportKeepsItsVersionAndNotesTheNewerOne() {
    importSeason("cricket-t20", SEASON_CSV);
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    int copied = body(get(commissioner, pool(roomId))).get("datasetVersion").asInt();

    importSeason(
        "cricket-t20", "name,country,role,tags,base_price,set\nOnly One,Chile,BAT,,20,Solo\n");
    JsonNode pool = body(get(commissioner, pool(roomId)));

    assertThat(pool.get("datasetVersion").asInt()).isEqualTo(copied);
    assertThat(pool.get("latestDatasetVersion").asInt()).isEqualTo(copied + 1);
    assertThat(pool.get("players")).hasSize(6);
    assertThat(body(get(commissioner, pool(createRoom(commissioner)))).get("players")).hasSize(1);
  }

  @Test
  void aManagerReadsTheSamePoolWithoutEditRightsAndAStrangerGets404() {
    importSeason("cricket-t20", SEASON_CSV);
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    Person manager = insertManager(roomId, "sky");

    JsonNode asManager = body(get(manager, pool(roomId)));

    assertThat(asManager.get("canEdit").asBoolean()).isFalse();
    assertThat(asManager.get("players")).hasSize(6);
    assertThat(get(person(), pool(roomId)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(null, pool(roomId)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void anInPlayRoomHasNoEditRightsEvenForTheCommissioner() {
    importSeason("cricket-t20", SEASON_CSV);
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    jdbc.update("UPDATE rooms SET state = 'auction_live' WHERE id = ?", UUID.fromString(roomId));

    JsonNode pool = body(get(commissioner, pool(roomId)));

    assertThat(pool.get("canEdit").asBoolean()).isFalse();
    assertThat(pool.get("players")).hasSize(6);
  }
}
