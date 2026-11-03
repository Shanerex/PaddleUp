package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.rooms.AbstractRoomsIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-6, AC-7 (create), AC-8 (refusals): redeeming a code creates one franchise per user. */
class JoinIT extends AbstractRoomsIT {

  private int franchiseCount(String roomId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM franchises WHERE room_id = ?",
        Integer.class,
        UUID.fromString(roomId));
  }

  private void setMaxManagers(Person commissioner, String roomId, int max) {
    ObjectNode config =
        (ObjectNode) body(get(commissioner, "/api/rooms/" + roomId)).get("config").deepCopy();
    config.put("maxManagers", max);
    var response =
        put(
            commissioner,
            "/api/rooms/" + roomId + "/config",
            Map.of("version", configVersion(commissioner, roomId), "config", config));
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void redeemingAValidCodeCreatesOneMemberWithAFranchise() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person ann = person();

    var response =
        post(
            ann,
            "/api/join",
            Map.of(
                "code",
                code,
                "franchise",
                Map.of("name", "<b>Ann XI</b>", "colour", "sky", "monogram", "AX")));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    var result = body(response);
    assertThat(result.get("roomId").asText()).isEqualTo(roomId);
    assertThat(result.get("franchise").get("name").asText()).isEqualTo("Ann XI");
    assertThat(result.get("franchise").get("colour").asText()).isEqualTo("sky");
    assertThat(result.get("franchise").get("ownerUserId").asText())
        .isEqualTo(userId(ann).toString());
    assertThat(franchiseCount(roomId)).isEqualTo(1);
    var room = body(get(ann, "/api/rooms/" + roomId));
    assertThat(room.get("myRole").asText()).isEqualTo("manager");
    assertThat(room.get("myFranchiseId").asText())
        .isEqualTo(result.get("franchise").get("id").asText());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM room_log WHERE room_id = ? AND kind = 'manager_joined'",
                Integer.class,
                UUID.fromString(roomId)))
        .isEqualTo(1);
  }

  @Test
  void redeemingAgainReturnsTheSameFranchiseAndTheCountDoesNotChange() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person ann = person();
    var first = body(join(ann, code, "sky"));

    var second = join(ann, code, "crimson");

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(body(second).get("franchise").get("id").asText())
        .isEqualTo(first.get("franchise").get("id").asText());
    assertThat(body(second).get("franchise").get("colour").asText()).isEqualTo("sky");
    assertThat(franchiseCount(roomId)).isEqualTo(1);
  }

  @Test
  void anUnknownOrMalformedCodeIsOneErrorAndCreatesNothing() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    issueInvite(commissioner, roomId);

    var unknown = join(person(), "ZZZZZZZZZZZZ", "sky");
    var malformed = join(person(), "!!", "sky");

    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(body(unknown).get("error").asText()).isEqualTo("invalid_invite");
    assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(malformed.getBody()).isEqualTo(unknown.getBody());
    assertThat(franchiseCount(roomId)).isZero();
  }

  @Test
  void aRevokedCodeFailsWithoutRemovingMembersAndARegeneratedOneWorks() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person ann = joinNew(roomId, code, "sky");

    delete(commissioner, "/api/rooms/" + roomId + "/invite");
    var late = join(person(), code, "crimson");

    assertThat(late.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(body(late).get("error").asText()).isEqualTo("invalid_invite");
    assertThat(franchiseCount(roomId)).isEqualTo(1);
    assertThat(get(ann, "/api/rooms/" + roomId).getStatusCode()).isEqualTo(HttpStatus.OK);

    String fresh = issueInvite(commissioner, roomId);
    assertThat(join(person(), code, "crimson").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(join(person(), fresh, "crimson").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(franchiseCount(roomId)).isEqualTo(2);
  }

  @Test
  void aCodeTypedWithLowerCaseAndDashesStillWorks() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    String typed =
        code.substring(0, 4).toLowerCase() + "-" + code.substring(4, 8) + " " + code.substring(8);

    assertThat(join(person(), typed, "sky").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void aFullRoomRefusesAndCreatesNoFranchise() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    setMaxManagers(commissioner, roomId, 2);
    String code = issueInvite(commissioner, roomId);
    joinNew(roomId, code, "crimson");
    joinNew(roomId, code, "sky");

    var third = join(person(), code, "teal");

    assertThat(third.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(third).get("error").asText()).isEqualTo("room_full");
    assertThat(franchiseCount(roomId)).isEqualTo(2);
  }

  @Test
  void aMemberRedeemingAgainInAFullRoomStillGetsTheirFranchise() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    setMaxManagers(commissioner, roomId, 2);
    String code = issueInvite(commissioner, roomId);
    Person ann = joinNew(roomId, code, "crimson");
    joinNew(roomId, code, "sky");

    assertThat(join(ann, code, "teal").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void aRoomThatIsAlreadyLiveRefusesEvenWithAnUnrevokedCode() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    jdbc.update("UPDATE rooms SET state = 'auction_live' WHERE id = ?", UUID.fromString(roomId));

    var response = join(person(), code, "sky");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(response).get("error").asText()).isEqualTo("room_locked");
    assertThat(franchiseCount(roomId)).isZero();
  }

  @Test
  void badFranchiseFieldsAreRejectedAndCreateNothing() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);

    for (var bad :
        java.util.List.of(
            Map.of("name", "A", "colour", "sky", "monogram", "AB"),
            Map.of("name", "x".repeat(31), "colour", "sky", "monogram", "AB"),
            Map.of("name", "Fine name", "colour", "mauve", "monogram", "AB"),
            Map.of("name", "Fine name", "colour", "sky", "monogram", "ABCD"))) {
      var response = post(person(), "/api/join", Map.of("code", code, "franchise", bad));
      assertThat(response.getStatusCode()).as(bad.toString()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    assertThat(post(person(), "/api/join", Map.of("code", code)).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(franchiseCount(roomId)).isZero();
  }

  @Test
  void aDuplicateNameIgnoringCaseOrATakenColourIs409() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    post(
        person(),
        "/api/join",
        Map.of(
            "code",
            code,
            "franchise",
            Map.of("name", "Super Kings", "colour", "sky", "monogram", "SK")));

    var sameName =
        post(
            person(),
            "/api/join",
            Map.of(
                "code",
                code,
                "franchise",
                Map.of("name", "SUPER kings", "colour", "teal", "monogram", "SK")));
    var sameColour =
        post(
            person(),
            "/api/join",
            Map.of(
                "code",
                code,
                "franchise",
                Map.of("name", "Other", "colour", "sky", "monogram", "OT")));

    assertThat(sameName.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(sameName).get("error").asText()).isEqualTo("duplicate");
    assertThat(sameColour.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(franchiseCount(roomId)).isEqualTo(1);
  }

  @Test
  void previewShowsTheRoomNameAndTakenColoursWithoutJoining() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    joinNew(roomId, code, "sky");
    Person visitor = person();

    var response = post(visitor, "/api/join/preview", Map.of("code", code));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var preview = body(response);
    assertThat(preview.get("roomName").asText()).startsWith("Room ");
    assertThat(preview.get("takenColours").get(0).asText()).isEqualTo("sky");
    assertThat(preview.get("full").asBoolean()).isFalse();
    assertThat(preview.get("alreadyJoined").asBoolean()).isFalse();
    assertThat(preview.has("roomId") && !preview.get("roomId").isNull()).isFalse();
    assertThat(franchiseCount(roomId)).isEqualTo(1);
    assertThat(post(visitor, "/api/join/preview", Map.of("code", "ZZZZZZZZZZZZ")).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void aSignedOutVisitorGets401() {
    assertThat(post(null, "/api/join", Map.of("code", "ABC")).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(post(null, "/api/join/preview", Map.of("code", "ABC")).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void theCommissionerCanAlsoRedeemTheirOwnCodeAndBecomeAPlayer() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);

    var response = join(commissioner, code, "sky");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(body(get(commissioner, "/api/rooms/" + roomId)).get("myRole").asText())
        .isEqualTo("commissioner");
  }
}
