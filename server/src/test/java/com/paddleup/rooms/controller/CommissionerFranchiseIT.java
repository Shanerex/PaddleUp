package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.rooms.AbstractRoomsIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-10: the Commissioner may own one franchise, or only administer the room. */
class CommissionerFranchiseIT extends AbstractRoomsIT {

  private int count(String roomId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM franchises WHERE room_id = ?",
        Integer.class,
        UUID.fromString(roomId));
  }

  private void setManagers(Person commissioner, String roomId, int min, int max) {
    ObjectNode config =
        (ObjectNode) body(get(commissioner, "/api/rooms/" + roomId)).get("config").deepCopy();
    config.put("minManagers", min).put("maxManagers", max);
    assertThat(
            put(
                    commissioner,
                    "/api/rooms/" + roomId + "/config",
                    Map.of("version", configVersion(commissioner, roomId), "config", config))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void theCommissionerCreatesTheirOwnFranchiseWithoutACode() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);

    var response =
        post(
            commissioner,
            "/api/rooms/" + roomId + "/franchises",
            Map.of("name", "<i>Boss</i> XI", "colour", "olive", "monogram", "BX"));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
    assertThat(body(response).get("name").asText()).isEqualTo("Boss XI");
    var room = body(get(commissioner, "/api/rooms/" + roomId));
    assertThat(room.get("franchiseCount").asInt()).isEqualTo(1);
    assertThat(room.get("myFranchiseId").asText()).isEqualTo(body(response).get("id").asText());
    assertThat(room.get("myRole").asText()).isEqualTo("commissioner");
    var members = body(get(commissioner, "/api/rooms/" + roomId + "/members"));
    assertThat(members).hasSize(1);
    assertThat(members.get(0).get("role").asText()).isEqualTo("commissioner");
    assertThat(members.get(0).get("franchise").get("colour").asText()).isEqualTo("olive");
  }

  @Test
  void aSecondFranchiseForTheCommissionerIs409() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String url = "/api/rooms/" + roomId + "/franchises";
    post(commissioner, url, franchiseBody("olive"));

    var second = post(commissioner, url, franchiseBody("sky"));

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(second).get("error").asText()).isEqualTo("duplicate");
    assertThat(count(roomId)).isEqualTo(1);
  }

  @Test
  void theFranchiseFollowsTheSameNameAndColourRules() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    join(person(), code, "sky");
    String url = "/api/rooms/" + roomId + "/franchises";

    assertThat(
            post(commissioner, url, Map.of("name", "A", "colour", "olive", "monogram", "A"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            post(commissioner, url, Map.of("name", "Fine", "colour", "sky", "monogram", "FN"))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(count(roomId)).isEqualTo(1);
  }

  @Test
  void itCountsTowardTheMaximumAndAFullRoomRefusesIt() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    setManagers(commissioner, roomId, 2, 2);
    String code = issueInvite(commissioner, roomId);
    joinNew(roomId, code, "crimson");
    joinNew(roomId, code, "sky");

    var response =
        post(commissioner, "/api/rooms/" + roomId + "/franchises", franchiseBody("teal"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(response).get("error").asText()).isEqualTo("room_full");
  }

  @Test
  void itCountsTowardTheMinimumToLeaveSetup() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    joinNew(roomId, code, "crimson");
    post(commissioner, "/api/rooms/" + roomId + "/franchises", franchiseBody("teal"));

    var advance =
        post(commissioner, "/api/rooms/" + roomId + "/transitions", Map.of("to", "retention"));

    assertThat(advance.getStatusCode()).as(advance.getBody()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void anAdminOnlyCommissionerHasNoFranchiseAndDoesNotCountTowardTheMaximum() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    setManagers(commissioner, roomId, 2, 2);
    String code = issueInvite(commissioner, roomId);
    joinNew(roomId, code, "crimson");
    joinNew(roomId, code, "sky");

    var room = body(get(commissioner, "/api/rooms/" + roomId));
    assertThat(room.get("myFranchiseId").isNull()).isTrue();
    assertThat(room.get("franchiseCount").asInt()).isEqualTo(2);
    assertThat(room.get("myRole").asText()).isEqualTo("commissioner");
    var members = body(get(commissioner, "/api/rooms/" + roomId + "/members"));
    assertThat(members).hasSize(3);
    assertThat(members.get(0).get("role").asText()).isEqualTo("commissioner");
    assertThat(members.get(0).get("franchise").isNull()).isTrue();
    assertThat(get(commissioner, "/api/rooms/" + roomId + "/invite").getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            post(commissioner, "/api/rooms/" + roomId + "/transitions", Map.of("to", "retention"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void aManagerCannotUseTheCommissionersFranchiseEndpoint() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person manager = joinNew(roomId, code, "crimson");

    var response = post(manager, "/api/rooms/" + roomId + "/franchises", franchiseBody("sky"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void membersAreVisibleToAnyMemberWithoutEmails() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person manager = joinNew(roomId, code, "crimson");

    var response = get(manager, "/api/rooms/" + roomId + "/members");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(body(response)).hasSize(2);
    assertThat(response.getBody()).doesNotContain("@example.test").doesNotContain("email");
  }
}
