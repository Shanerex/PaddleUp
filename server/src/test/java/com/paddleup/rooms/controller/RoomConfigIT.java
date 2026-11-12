package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.rooms.AbstractRoomsIT;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * AC-2, AC-3: validation lists every violation; only the Commissioner saves, before the auction.
 */
class RoomConfigIT extends AbstractRoomsIT {

  private ObjectNode config(Person who, String roomId) {
    return (ObjectNode) body(get(who, "/api/rooms/" + roomId)).get("config").deepCopy();
  }

  private ResponseEntity<String> save(Person who, String roomId, int version, ObjectNode config) {
    return put(
        who, "/api/rooms/" + roomId + "/config", Map.of("version", version, "config", config));
  }

  private List<String> violationFields(ResponseEntity<String> response) {
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body(response).get("error").asText()).isEqualTo("invalid_config");
    return body(response).get("violations").findValuesAsText("field");
  }

  private List<String> violationsFor(Consumer<ObjectNode> edit, int franchises) {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    for (int i = 0; i < franchises; i++) {
      insertManager(roomId, COLOURS.get(i));
    }
    ObjectNode config = config(commissioner, roomId);
    edit.accept(config);
    return violationFields(save(commissioner, roomId, 1, config));
  }

  @Test
  void aStoredConfigWithoutTimersReadsTheDefaultsAndTimersSaveWithinRange() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    jdbc.update(
        "UPDATE rooms SET config = config - 'openSeconds' - 'bidSeconds' - 'gapSeconds'"
            + " WHERE id = ?",
        UUID.fromString(roomId));

    ObjectNode stored = config(commissioner, roomId);
    assertThat(stored.get("openSeconds").asInt()).isEqualTo(20);
    assertThat(stored.get("bidSeconds").asInt()).isEqualTo(10);
    assertThat(stored.get("gapSeconds").asInt()).isEqualTo(5);
    stored.put("openSeconds", 30).put("bidSeconds", 8).put("gapSeconds", 0);
    var saved = save(commissioner, roomId, 1, stored);

    assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode read = body(get(commissioner, "/api/rooms/" + roomId)).get("config");
    assertThat(read.get("openSeconds").asInt()).isEqualTo(30);
    assertThat(read.get("bidSeconds").asInt()).isEqualTo(8);
    assertThat(read.get("gapSeconds").asInt()).isEqualTo(0);
  }

  @Test
  void aNewRoomCarriesTheDefaultTimersAndOutOfRangeTimersAreRefused() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    ObjectNode config = config(commissioner, roomId);
    assertThat(config.get("openSeconds").asInt()).isEqualTo(20);
    assertThat(config.get("bidSeconds").asInt()).isEqualTo(10);
    assertThat(config.get("gapSeconds").asInt()).isEqualTo(5);

    config.put("openSeconds", 4).put("bidSeconds", 61).put("gapSeconds", 31);

    assertThat(violationFields(save(commissioner, roomId, 1, config)))
        .containsExactlyInAnyOrder("openSeconds", "bidSeconds", "gapSeconds");
    assertThat(configVersion(commissioner, roomId)).isEqualTo(1);
  }

  @Test
  void aValidSavePersistsBumpsTheVersionAndIsLogged() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    ObjectNode config = config(commissioner, roomId);
    config.put("purse", 9999).put("name", "New <i>name</i>");
    config
        .putObject("ai")
        .put("enabled", false)
        .put("reportCardVisibility", "private")
        .putObject("lifelines")
        .put("fit-check", 0)
        .put("price-ceiling", 2)
        .put("rival-scan", 1);

    var response = save(commissioner, roomId, 1, config);

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode room = body(get(commissioner, "/api/rooms/" + roomId));
    assertThat(room.get("configVersion").asInt()).isEqualTo(2);
    assertThat(room.get("config").get("purse").asLong()).isEqualTo(9999);
    assertThat(room.get("config").get("name").asText()).isEqualTo("New name");
    assertThat(room.get("config").get("ai").get("enabled").asBoolean()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM room_log WHERE room_id = ? AND kind = 'config_saved'",
                Integer.class,
                UUID.fromString(roomId)))
        .isEqualTo(1);
  }

  @Test
  void aPurseBelowABasePriceInTheRoomsPoolIsRejectedAndNothingChanges() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    long dearest =
        jdbc.queryForObject(
            "SELECT max(base_price) FROM room_pool_players WHERE room_id = ?",
            Long.class,
            UUID.fromString(roomId));
    ObjectNode config = config(commissioner, roomId);
    config.put("purse", dearest - 1);

    var response = save(commissioner, roomId, 1, config);

    assertThat(violationFields(response)).containsExactly("purse");
    assertThat(body(response).get("violations").get(0).get("message").asText())
        .contains("Change those players first");
    JsonNode room = body(get(commissioner, "/api/rooms/" + roomId));
    assertThat(room.get("configVersion").asInt()).isEqualTo(1);
    assertThat(room.get("config").get("purse").asLong()).isNotEqualTo(dearest - 1);

    config.put("purse", dearest);
    assertThat(save(commissioner, roomId, 1, config).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void roleMinimumsAboveSquadMinNamesRoleMinimums() {
    assertThat(violationsFor(c -> c.put("squadMin", 10).put("squadMax", 12), 0))
        .containsExactly("roleMinimums");
  }

  @Test
  void squadMinAboveSquadMaxNamesSquadMin() {
    assertThat(violationsFor(c -> c.put("squadMax", 15), 0)).containsExactly("squadMin");
  }

  @Test
  void maxManagersBelowTheFranchisesInTheRoomNamesMaxManagers() {
    assertThat(violationsFor(c -> c.put("minManagers", 2).put("maxManagers", 2), 3))
        .containsExactly("maxManagers");
  }

  @Test
  void zeroPurseNamesPurse() {
    assertThat(violationsFor(c -> c.put("purse", 0), 0)).containsExactly("purse");
  }

  @Test
  void emptyLadderNamesIncrementLadder() {
    assertThat(violationsFor(c -> c.set("incrementLadder", json.createArrayNode()), 0))
        .containsExactly("incrementLadder");
  }

  @Test
  void negativeQuotaNamesThatCategory() {
    assertThat(
            violationsFor(c -> ((ObjectNode) c.get("categoryQuotas")).put("overseas-cap", -1), 0))
        .containsExactly("categoryQuotas.overseas-cap");
  }

  @Test
  void unknownRoleIdNamesThatRole() {
    assertThat(violationsFor(c -> ((ObjectNode) c.get("roleMinimums")).put("ZZ", 1), 0))
        .containsExactly("roleMinimums.ZZ");
  }

  @Test
  void aClientSentChangeToTheScoringRulesIsIgnored() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    JsonNode before = config(commissioner, roomId).get("scoringRules");
    ObjectNode edited = config(commissioner, roomId);
    ((ObjectNode) ((ArrayNode) edited.get("scoringRules").get("rules")).get(0))
        .put("stat", "not-a-stat");
    ((ArrayNode) edited.get("scoringRules").get("rules")).remove(1);
    edited.put("purse", 4321);

    var saved = save(commissioner, roomId, 1, edited);

    assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode after = config(commissioner, roomId);
    assertThat(after.get("purse").asLong()).isEqualTo(4321);
    assertThat(after.get("scoringRules")).isEqualTo(before);
  }

  /** Saves a config whose first scoring rule is a role-conditional chain {@code depth} deep. */
  private ResponseEntity<String> saveNestedRule(int depth) {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    ObjectNode config = config(commissioner, roomId);
    ((ObjectNode) config.get("scoringRules")).set("rules", json.createArrayNode());
    String chain =
        "{\"type\":\"role-conditional\",\"id\":\"c\",\"roles\":[],\"rule\":".repeat(depth)
            + "{\"type\":\"appearance-bonus\",\"id\":\"a\",\"points\":1}"
            + "}".repeat(depth);
    String payload =
        "{\"version\":1,\"config\":"
            + config.toString().replace("\"rules\":[]", "\"rules\":[" + chain + "]")
            + "}";
    return call(
        org.springframework.http.HttpMethod.PUT,
        "/api/rooms/" + roomId + "/config",
        commissioner.cookie(),
        payload);
  }

  // Security review S-6: an over-deep rule chain never overflows the stack or gives a 500; the
  // rules are ignored on save, so a chain inside the parser limit is dropped.
  @Test
  void anOverDeepRuleChainIsDroppedNotStored() {
    var response = saveNestedRule(20);
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).doesNotContain("\"id\":\"c\"");
  }

  @Test
  void aRuleChainBeyondTheParserLimitIs400() {
    var response = saveNestedRule(5000);
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  /** 100 rules, each 50 bands, as one scoring-rule list. */
  private static String bandRules(boolean wrapped, String band) {
    String bands = ("," + band).repeat(50).substring(1);
    String banded =
        "{\"type\":\"banded-rate\",\"id\":\"b\",\"numerator\":\"runs\","
            + "\"denominator\":\"balls-faced\",\"scale\":1,\"bands\":["
            + bands
            + "]}";
    String roles = ",\"BAT\"".repeat(50).substring(1);
    String rule =
        wrapped
            ? "{\"type\":\"role-conditional\",\"id\":\"w\",\"roles\":["
                + roles
                + "],\"rule\":"
                + banded
                + "}"
            : banded;
    return "[" + String.join(",", java.util.Collections.nCopies(100, rule)) + "]";
  }

  // QA T-3 / security S-9: the largest config the validator accepts is either refused with 400
  // or saved and still readable; the room is never bricked with a 500.
  private void saveLargestThenRead(String rules) {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    ObjectNode config = config(commissioner, roomId);
    ((ObjectNode) config.get("scoringRules")).set("rules", json.createArrayNode());
    String payload =
        "{\"version\":1,\"config\":"
            + config.toString().replace("\"rules\":[]", "\"rules\":" + rules)
            + "}";
    var saved =
        call(
            org.springframework.http.HttpMethod.PUT,
            "/api/rooms/" + roomId + "/config",
            commissioner.cookie(),
            payload);

    assertThat(saved.getStatusCode().value()).as(saved.getBody()).isIn(200, 400);
    var read = get(commissioner, "/api/rooms/" + roomId);
    assertThat(read.getStatusCode()).as(read.getBody()).isEqualTo(HttpStatus.OK);
    // The repair path works too: a later save of the same room is not 500.
    int version = body(read).get("configVersion").asInt();
    // The name is changed, not the purse: a purse could fall below a base price in whichever
    // season dataset earlier tests left as the latest.
    var again =
        save(commissioner, roomId, version, config(commissioner, roomId).put("name", "Repaired"));
    assertThat(again.getStatusCode()).as(again.getBody()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void aLargeRoleConditionalConfigOfEmptyBandsStaysReadable() {
    saveLargestThenRead(bandRules(true, "{}"));
  }

  @Test
  void aLargeConfigOfExtremeBandValuesStaysReadable() {
    saveLargestThenRead(bandRules(false, "{\"from\":-1e6,\"to\":-1e6,\"points\":-1e6}"));
  }

  @Test
  void everyViolationIsListedTogether() {
    var fields =
        violationsFor(
            c -> {
              c.put("purse", 0);
              c.set("incrementLadder", json.createArrayNode());
              c.put("minManagers", 1);
            },
            0);
    assertThat(fields).containsExactlyInAnyOrder("purse", "incrementLadder", "minManagers");
  }

  @Test
  void aMissingOrUnreadableConfigIs400() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);

    var missing = put(commissioner, "/api/rooms/" + roomId + "/config", Map.of("version", 1));
    var wrongType =
        put(
            commissioner,
            "/api/rooms/" + roomId + "/config",
            Map.of("version", 1, "config", Map.of("purse", "lots")));

    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(wrongType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void aManagerCannotSaveAndNothingChanges() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    Person manager = insertManager(roomId, "crimson");
    ObjectNode config = config(commissioner, roomId).put("purse", 5);

    var response = save(manager, roomId, 1, config);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(configVersion(commissioner, roomId)).isEqualTo(1);
  }

  @Test
  void saveIsRefusedOnceTheAuctionIsLiveAndTheStaleTabIsTold() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    ObjectNode config = config(commissioner, roomId).put("purse", 5000);
    jdbc.update("UPDATE rooms SET state = 'auction_live' WHERE id = ?", UUID.fromString(roomId));

    var response = save(commissioner, roomId, 1, config);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(response).get("error").asText()).isEqualTo("room_locked");
    assertThat(body(get(commissioner, "/api/rooms/" + roomId)).get("config").get("purse").asLong())
        .isNotEqualTo(5000);
  }

  @Test
  void savingStillWorksInRetentionAndAuctionScheduled() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    jdbc.update("UPDATE rooms SET state = 'retention' WHERE id = ?", UUID.fromString(roomId));
    assertThat(save(commissioner, roomId, 1, config(commissioner, roomId)).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    jdbc.update(
        "UPDATE rooms SET state = 'auction_scheduled' WHERE id = ?", UUID.fromString(roomId));
    assertThat(save(commissioner, roomId, 2, config(commissioner, roomId)).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void aStaleVersionIs409AndChangesNothing() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    assertThat(
            save(commissioner, roomId, 1, config(commissioner, roomId).put("purse", 7000))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    var stale = save(commissioner, roomId, 1, config(commissioner, roomId).put("purse", 1234));

    assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(stale).get("error").asText()).isEqualTo("stale_version");
    JsonNode room = body(get(commissioner, "/api/rooms/" + roomId));
    assertThat(room.get("config").get("purse").asLong()).isEqualTo(7000);
    assertThat(room.get("configVersion").asInt()).isEqualTo(2);
  }
}
