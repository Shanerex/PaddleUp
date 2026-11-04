package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.rooms.AbstractRoomsIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-12: commissionership moves to a manager with a franchise, in any state but completed. */
class HandoverIT extends AbstractRoomsIT {

  private String franchiseOf(String roomId, String colour) {
    return jdbc.queryForObject(
        "SELECT id::text FROM franchises WHERE room_id = ? AND colour = ?",
        String.class,
        UUID.fromString(roomId),
        colour);
  }

  private String url(String roomId) {
    return "/api/rooms/" + roomId + "/commissioner";
  }

  @Test
  void aManagerBecomesTheSoleCommissionerAndAnAdminOnlyOldOneLeavesTheRoom() {
    Person old = person();
    String roomId = createRoom(old);
    Person ann = joinNew(roomId, issueInvite(old, roomId), "sky");

    var response = post(old, url(roomId), Map.of("franchiseId", franchiseOf(roomId, "sky")));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(body(response).get("stillMember").asBoolean()).isFalse();
    assertThat(body(response).get("commissioner").get("id").asText())
        .isEqualTo(userId(ann).toString());
    assertThat(body(get(ann, "/api/rooms/" + roomId)).get("myRole").asText())
        .isEqualTo("commissioner");
    assertThat(get(old, "/api/rooms/" + roomId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM room_log WHERE room_id = ? AND kind = 'commissioner_handover'",
                Integer.class,
                UUID.fromString(roomId)))
        .isEqualTo(1);
  }

  @Test
  void anOldCommissionerWithAFranchiseStaysAManager() {
    Person old = person();
    String roomId = createRoom(old);
    post(old, "/api/rooms/" + roomId + "/franchises", franchiseBody("olive"));
    Person ann = joinNew(roomId, issueInvite(old, roomId), "sky");

    var response = post(old, url(roomId), Map.of("franchiseId", franchiseOf(roomId, "sky")));

    assertThat(body(response).get("stillMember").asBoolean()).isTrue();
    assertThat(body(get(old, "/api/rooms/" + roomId)).get("myRole").asText()).isEqualTo("manager");
    assertThat(get(old, "/api/rooms/" + roomId + "/invite").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(get(ann, "/api/rooms/" + roomId + "/invite").getStatusCode())
        .isIn(HttpStatus.NOT_FOUND, HttpStatus.OK);
    assertThat(
            jdbc.queryForObject(
                "SELECT commissioner_user_id = ? FROM rooms WHERE id = ?",
                Boolean.class,
                userId(ann),
                UUID.fromString(roomId)))
        .isTrue();
  }

  @Test
  void aFranchiseOutsideTheRoomOrNoFranchiseIdIs400() {
    Person old = person();
    String roomId = createRoom(old);
    Person other = person();
    String otherRoom = createRoom(other);
    joinNew(otherRoom, issueInvite(other, otherRoom), "sky");

    assertThat(
            post(old, url(roomId), Map.of("franchiseId", franchiseOf(otherRoom, "sky")))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            post(old, url(roomId), Map.of("franchiseId", UUID.randomUUID().toString()))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(old, url(roomId), Map.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void handingToYourselfIs400() {
    Person old = person();
    String roomId = createRoom(old);
    post(old, "/api/rooms/" + roomId + "/franchises", franchiseBody("olive"));

    var response = post(old, url(roomId), Map.of("franchiseId", franchiseOf(roomId, "olive")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void aManagerCannotHandOverAndNothingChanges() {
    Person old = person();
    String roomId = createRoom(old);
    String code = issueInvite(old, roomId);
    Person ann = joinNew(roomId, code, "sky");
    joinNew(roomId, code, "teal");

    var response = post(ann, url(roomId), Map.of("franchiseId", franchiseOf(roomId, "teal")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(body(get(old, "/api/rooms/" + roomId)).get("myRole").asText())
        .isEqualTo("commissioner");
  }

  @Test
  void handoverWorksInAuctionLiveButNotInCompleted() {
    Person old = person();
    String roomId = createRoom(old);
    String code = issueInvite(old, roomId);
    Person ann = joinNew(roomId, code, "sky");
    joinNew(roomId, code, "teal");
    jdbc.update("UPDATE rooms SET state = 'auction_live' WHERE id = ?", UUID.fromString(roomId));

    assertThat(
            post(old, url(roomId), Map.of("franchiseId", franchiseOf(roomId, "sky")))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    jdbc.update("UPDATE rooms SET state = 'completed' WHERE id = ?", UUID.fromString(roomId));
    var completed = post(ann, url(roomId), Map.of("franchiseId", franchiseOf(roomId, "teal")));
    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(completed).get("error").asText()).isEqualTo("illegal_state");
  }
}
