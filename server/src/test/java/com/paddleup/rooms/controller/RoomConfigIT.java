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
  void aScoringRuleOnAnUnknownStatNamesTheRule() {
    assertThat(
            violationsFor(
                c ->
                    ((ObjectNode) ((ArrayNode) c.get("scoringRules").get("rules")).get(0))
                        .put("stat", "not-a-stat"),
                0))
        .containsExactly("scoringRules.rules[0].stat");
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

  // Security review S-6: an over-deep rule chain is a 400, never a stack overflow or a 500.
  @Test
  void anOverDeepRuleChainIsRefusedAsAViolation() {
    assertThat(violationFields(saveNestedRule(20)))
        .containsExactly("scoringRules.rules[0].rule.rule.rule");
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
    var again = save(commissioner, roomId, version, config(commissioner, roomId).put("purse", 77));
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
  void aConfigPastTheSavedSizeCapIs400NotStored() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    ObjectNode config = config(commissioner, roomId);
    ArrayNode rules = (ArrayNode) config.get("scoringRules").get("rules");
    rules.removeAll();
    for (int i = 0; i < 100; i++) {
      ObjectNode rule = rules.addObject();
      rule.put("type", "banded-rate").put("id", "b" + i);
      rule.put("numerator", "runs").put("denominator", "balls-faced").put("scale", 1);
      ArrayNode bands = rule.putArray("bands");
      for (int b = 0; b < 50; b++) {
        bands.addObject().put("from", 1).put("to", 2).put("points", 3);
      }
    }
    // Under the 256 KB request cap, so the saved-size cap is what refuses it.
    assertThat(config.toString().length()).isLessThan(256 * 1024);

    var response = save(commissioner, roomId, 1, config);

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body(response).get("error").asText()).isEqualTo("invalid_config");
    assertThat(body(get(commissioner, "/api/rooms/" + roomId)).get("configVersion").asInt())
        .isEqualTo(1);
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
