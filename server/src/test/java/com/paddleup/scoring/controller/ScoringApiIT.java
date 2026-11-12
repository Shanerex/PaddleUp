package com.paddleup.scoring.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.scoring.ScoringIT;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** AC-11 to AC-15, AC-18: the rules and points over real HTTP, with the error codes. */
class ScoringApiIT extends ScoringIT {

  private String base() {
    return "/api/rooms/" + roomId + "/scoring";
  }

  private JsonNode view(Person who) {
    var response = get(who, base() + "/rules");
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    return body(response);
  }

  /** The current rules with the points per run set to {@code points}. */
  private ObjectNode runsWorth(Person who, int points) {
    ObjectNode rules = (ObjectNode) view(who).get("rules").deepCopy();
    for (JsonNode rule : (ArrayNode) rules.get("rules")) {
      if ("runs-points".equals(rule.get("id").asText())) {
        ((ObjectNode) rule).put("points", points);
      }
    }
    return rules;
  }

  private Map<String, Object> edit(int version, JsonNode rules) {
    var request = new LinkedHashMap<String, Object>();
    request.put("version", version);
    request.put("rules", rules);
    return request;
  }

  private Map<String, Object> change(int version, JsonNode rules, String reason, boolean attested) {
    var request = edit(version, rules);
    request.put("reason", reason);
    request.put("attested", attested);
    return request;
  }

  private void assertCode(ResponseEntity<String> response, HttpStatus status, String error) {
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
    assertThat(body(response).get("error").asText()).isEqualTo(error);
  }

  private void freeze() {
    clock.freeze(START.plusSeconds(1));
  }

  // ---- read ----

  @Test
  void aMemberReadsTheRulesAndANonMemberOrAnonymousCallerIsRefused() {
    var read = view(manager);

    assertThat(read.get("status").asText()).isEqualTo("pack-default");
    assertThat(read.get("version").asInt()).isZero();
    assertThat(read.get("frozen").asBoolean()).isFalse();
    assertThat(read.get("packVersion").asText()).isEqualTo("1.0.0");
    assertThat(read.get("freezesAt").asText()).isEqualTo("2027-04-01T14:00:00Z");
    assertThat(read.get("rules").get("rules")).isNotEmpty();
    assertThat(read.get("rules").get("rules").get(0).get("label").asText()).isEqualTo("Runs");

    assertCode(get(person(), base() + "/rules"), HttpStatus.NOT_FOUND, "room_not_found");
    assertThat(get(null, base() + "/rules").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ---- 403 for a non-Commissioner on every write, in every state ----

  @Test
  void aManagerGets403OnEveryWriteBeforeAndAfterTheFreeze() {
    for (int state = 0; state < 2; state++) {
      if (state == 1) {
        freeze();
      }
      var rules = runsWorth(manager, 2);
      assertCode(
          put(manager, base() + "/rules", edit(0, rules)), HttpStatus.FORBIDDEN, "forbidden");
      assertCode(
          post(manager, base() + "/rules/revert", Map.of("version", 0)),
          HttpStatus.FORBIDDEN,
          "forbidden");
      assertCode(
          post(manager, base() + "/rules/post-freeze-change", change(0, rules, "Because", true)),
          HttpStatus.FORBIDDEN,
          "forbidden");
    }
    assertThat(logCount(roomId)).isZero();
    assertThat(version(roomId)).isZero();
  }

  @Test
  void aNonMemberGets404OnEveryRoute() {
    Person stranger = person();
    var rules = runsWorth(manager, 2);

    assertCode(
        put(stranger, base() + "/rules", edit(0, rules)), HttpStatus.NOT_FOUND, "room_not_found");
    assertCode(
        post(stranger, base() + "/rules/revert", Map.of("version", 0)),
        HttpStatus.NOT_FOUND,
        "room_not_found");
    assertCode(
        post(stranger, base() + "/rules/post-freeze-change", change(0, rules, "x", true)),
        HttpStatus.NOT_FOUND,
        "room_not_found");
    assertCode(get(stranger, base() + "/rules/log"), HttpStatus.NOT_FOUND, "room_not_found");
    assertCode(
        get(stranger, base() + "/matches/m1/points"), HttpStatus.NOT_FOUND, "room_not_found");
  }

  // ---- edit, revert ----

  @Test
  void theCommissionerEditsAndRevertsAndEveryoneSeesTheNewState() {
    var edited = put(commissioner, base() + "/rules", edit(0, runsWorth(commissioner, 2)));

    assertThat(edited.getStatusCode()).as(edited.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(body(edited).get("status").asText()).isEqualTo("custom");
    assertThat(body(edited).get("version").asInt()).isEqualTo(1);
    assertThat(view(manager).get("status").asText()).isEqualTo("custom");

    var reverted = post(commissioner, base() + "/rules/revert", Map.of("version", 1));

    assertThat(reverted.getStatusCode()).as(reverted.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(body(reverted).get("status").asText()).isEqualTo("pack-default");
    assertThat(body(reverted).get("version").asInt()).isEqualTo(2);
  }

  @Test
  void anEditAfterTheStartIs409FrozenAndAStaleVersionIs409() {
    put(commissioner, base() + "/rules", edit(0, runsWorth(commissioner, 2)));
    assertCode(
        put(commissioner, base() + "/rules", edit(0, runsWorth(commissioner, 3))),
        HttpStatus.CONFLICT,
        "stale_version");

    freeze();

    assertCode(
        put(commissioner, base() + "/rules", edit(1, runsWorth(commissioner, 3))),
        HttpStatus.CONFLICT,
        "frozen");
    assertCode(
        post(commissioner, base() + "/rules/revert", Map.of("version", 1)),
        HttpStatus.CONFLICT,
        "frozen");
    assertThat(view(manager).get("frozen").asBoolean()).isTrue();
  }

  @Test
  void anInvalidSetIs422ListingEveryFieldAndSavesNothing() {
    ObjectNode rules = runsWorth(commissioner, 2);
    ArrayNode list = (ArrayNode) rules.get("rules");
    ((ObjectNode) list.get(0)).put("stat", "nope");
    ((ObjectNode) list.get(1)).put("label", "x".repeat(61));
    ((ObjectNode) list.get(2)).put("points", 2000000);

    var response = put(commissioner, base() + "/rules", edit(0, rules));

    assertCode(response, HttpStatus.UNPROCESSABLE_ENTITY, "invalid_rule_set");
    assertThat(body(response).get("violations").findValuesAsText("field"))
        .containsExactly("rules[0].stat", "rules[1].label", "rules[2].points");
    assertThat(version(roomId)).isZero();
    assertThat(view(commissioner).get("status").asText()).isEqualTo("pack-default");
  }

  @Test
  void aMissingOrUnreadableBodyIs400() {
    assertCode(
        put(commissioner, base() + "/rules", Map.of("version", "x")),
        HttpStatus.BAD_REQUEST,
        "invalid_request");
    var noRules = put(commissioner, base() + "/rules", Map.of("version", 0));
    assertCode(noRules, HttpStatus.UNPROCESSABLE_ENTITY, "invalid_rule_set");
  }

  @Test
  void aWriteWithoutAVersionIs400AndSavesNothing() {
    var rules = runsWorth(commissioner, 2);
    var noVersion = new LinkedHashMap<String, Object>();
    noVersion.put("rules", rules);
    var postFreeze = new LinkedHashMap<String, Object>(noVersion);
    postFreeze.put("reason", "Because");
    postFreeze.put("attested", true);

    assertCode(
        put(commissioner, base() + "/rules", noVersion), HttpStatus.BAD_REQUEST, "invalid_request");
    assertCode(
        post(commissioner, base() + "/rules/revert", Map.of()),
        HttpStatus.BAD_REQUEST,
        "invalid_request");
    assertCode(
        post(commissioner, base() + "/rules/post-freeze-change", postFreeze),
        HttpStatus.BAD_REQUEST,
        "invalid_request");
    freeze();
    assertCode(
        post(commissioner, base() + "/rules/post-freeze-change", postFreeze),
        HttpStatus.BAD_REQUEST,
        "invalid_request");
    assertThat(logCount(roomId)).isZero();
    assertThat(version(roomId)).isZero();
  }

  // ---- post-freeze change and log ----

  @Test
  void aPostFreezeChangeNeedsAReasonAndAttestationAndIsNotFrozenBefore() {
    var rules = runsWorth(commissioner, 2);
    assertCode(
        post(commissioner, base() + "/rules/post-freeze-change", change(0, rules, "Reason", true)),
        HttpStatus.CONFLICT,
        "not_frozen");

    freeze();

    var missing =
        post(commissioner, base() + "/rules/post-freeze-change", change(0, rules, " ", false));
    assertCode(missing, HttpStatus.UNPROCESSABLE_ENTITY, "invalid_rule_set");
    assertThat(body(missing).get("violations").findValuesAsText("field"))
        .containsExactly("reason", "attested");
    assertThat(logCount(roomId)).isZero();
  }

  @Test
  void aPostFreezeChangeSavesAndEveryMemberReadsTheLogWithTheOldSet() {
    freeze();
    var rules = runsWorth(commissioner, 2);

    var changed =
        post(
            commissioner,
            base() + "/rules/post-freeze-change",
            change(0, rules, "The group agreed", true));

    assertThat(changed.getStatusCode()).as(changed.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(body(changed).get("status").asText()).isEqualTo("custom");
    var log = body(get(manager, base() + "/rules/log"));
    assertThat(log).hasSize(1);
    var entry = log.get(0);
    assertThat(entry.get("kind").asText()).isEqualTo("post_freeze_change");
    assertThat(entry.get("reason").asText()).isEqualTo("The group agreed");
    assertThat(entry.get("attested").asBoolean()).isTrue();
    assertThat(entry.get("actor").asText()).isEqualTo(userId(commissioner).toString());
    assertThat(entry.get("occurredAt").asText()).isEqualTo("2027-04-01T14:00:01Z");
    assertThat(entry.get("diff").get("changed").get(0).asText()).isEqualTo("runs-points");
    assertThat(entry.get("before").get("rules")).isNotEmpty();
    assertThat(entry.get("after")).isEqualTo(rules);
  }

  // ---- points ----

  @Test
  void aMemberReadsTheBreakdownOfAMatch() {
    provider(datasetId, "m1", player.sourcePlayerId(), "30");

    var response = get(manager, base() + "/matches/m1/points");

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    var points = body(response);
    assertThat(points.get("matchId").asText()).isEqualTo("m1");
    assertThat(points.get("players")).hasSize(1);
    var one = points.get("players").get(0);
    assertThat(one.get("poolPlayerId").asText()).isEqualTo(player.poolPlayerId().toString());
    assertThat(one.get("total").decimalValue()).isEqualByComparingTo("34");
    assertThat(one.get("lines").findValuesAsText("ruleId"))
        .containsExactly("runs-points", "appearance-bonus");
    assertThat(one.get("lines").get(0).get("label").asText()).isEqualTo("Runs");
    assertThat(one.get("ruleSetVersion").asInt()).isZero();
  }

  @Test
  void aMatchWithNoPointsReadsEmpty() {
    var response = get(commissioner, base() + "/matches/none/points");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(body(response).get("players")).isEmpty();
  }
}
