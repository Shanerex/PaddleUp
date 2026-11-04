package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.rooms.AbstractRoomsIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-11, AC-8 (slot freed): the Commissioner removes a manager before the auction is live. */
class RemoveManagerIT extends AbstractRoomsIT {

  private String franchiseOf(String roomId, String colour) {
    return jdbc.queryForObject(
        "SELECT id::text FROM franchises WHERE room_id = ? AND colour = ?",
        String.class,
        UUID.fromString(roomId),
        colour);
  }

  private int count(String roomId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM franchises WHERE room_id = ?",
        Integer.class,
        UUID.fromString(roomId));
  }

  private String removeUrl(String roomId, String franchiseId) {
    return "/api/rooms/" + roomId + "/franchises/" + franchiseId;
  }

  @Test
  void removesAManagerInRetentionAndFreesTheSlotForARejoin() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person ann = joinNew(roomId, code, "sky");
    joinNew(roomId, code, "teal");
    post(commissioner, "/api/rooms/" + roomId + "/transitions", Map.of("to", "retention"));
    String annFranchise = franchiseOf(roomId, "sky");

    var response = delete(commissioner, removeUrl(roomId, annFranchise));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(body(response)).hasSize(2);
    assertThat(count(roomId)).isEqualTo(1);
    assertThat(get(ann, "/api/rooms/" + roomId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM room_log WHERE room_id = ? AND kind = 'manager_removed'",
                Integer.class,
                UUID.fromString(roomId)))
        .isEqualTo(1);
    var rejoin = join(ann, code, "sky");
    assertThat(rejoin.getStatusCode()).as(rejoin.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(count(roomId)).isEqualTo(2);
  }

  @Test
  void removingBelowTheMinimumInRetentionBlocksSchedulingUntilRefilled() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    joinNew(roomId, code, "sky");
    joinNew(roomId, code, "teal");
    post(commissioner, "/api/rooms/" + roomId + "/transitions", Map.of("to", "retention"));
    delete(commissioner, removeUrl(roomId, franchiseOf(roomId, "sky")));
    delete(commissioner, removeUrl(roomId, franchiseOf(roomId, "teal")));

    var refused =
        post(
            commissioner,
            "/api/rooms/" + roomId + "/transitions",
            Map.of("to", "auction_scheduled"));

    assertThat(refused.getStatusCode()).as(refused.getBody()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(refused).get("error").asText()).isEqualTo("illegal_state");
  }

  @Test
  void aFullRoomTakesSomeoneNewAfterARemoval() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    var config = body(get(commissioner, "/api/rooms/" + roomId)).get("config").deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) config).put("maxManagers", 2);
    put(commissioner, "/api/rooms/" + roomId + "/config", Map.of("version", 1, "config", config));
    String code = issueInvite(commissioner, roomId);
    joinNew(roomId, code, "crimson");
    joinNew(roomId, code, "sky");
    Person late = person();
    assertThat(join(late, code, "teal").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    delete(commissioner, removeUrl(roomId, franchiseOf(roomId, "crimson")));

    assertThat(join(late, code, "teal").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void removalIsRefusedWhenTheAuctionIsLive() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    joinNew(roomId, issueInvite(commissioner, roomId), "sky");
    jdbc.update("UPDATE rooms SET state = 'auction_live' WHERE id = ?", UUID.fromString(roomId));

    var response = delete(commissioner, removeUrl(roomId, franchiseOf(roomId, "sky")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(response).get("error").asText()).isEqualTo("room_locked");
    assertThat(count(roomId)).isEqualTo(1);
  }

  @Test
  void removingYourOwnFranchiseIs400() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String own =
        body(post(commissioner, "/api/rooms/" + roomId + "/franchises", franchiseBody("olive")))
            .get("id")
            .asText();

    var response = delete(commissioner, removeUrl(roomId, own));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(count(roomId)).isEqualTo(1);
  }

  @Test
  void aManagerCannotRemoveAnotherManager() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person ann = joinNew(roomId, code, "sky");
    joinNew(roomId, code, "teal");

    var response = delete(ann, removeUrl(roomId, franchiseOf(roomId, "teal")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(count(roomId)).isEqualTo(2);
  }

  @Test
  void aNonMemberGets404AndAnUnknownFranchiseIs404() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    joinNew(roomId, issueInvite(commissioner, roomId), "sky");

    assertThat(delete(person(), removeUrl(roomId, franchiseOf(roomId, "sky"))).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            delete(commissioner, removeUrl(roomId, UUID.randomUUID().toString())).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
