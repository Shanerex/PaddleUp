package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.rooms.AbstractRoomsIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

/** AC-17: a second pack runs a room with zero core changes. */
@TestPropertySource(properties = "paddleup.packs.dir=src/test/resources/packs/stub")
class StubPackRoomIT extends AbstractRoomsIT {

  @Test
  void aRoomIsCreatedAndJoinedFromTheStubPack() {
    Person commissioner = person();
    var packs = body(get(commissioner, "/api/rooms/packs"));
    assertThat(packs).hasSize(1);
    assertThat(packs.get(0).get("id").asText()).isEqualTo("stub-football");

    String roomId = createRoom(commissioner, "stub-football", "standard");
    var room = body(get(commissioner, "/api/rooms/" + roomId));

    assertThat(room.get("state").asText()).isEqualTo("setup");
    assertThat(room.get("config").get("roleMinimums").get("gk").asInt()).isEqualTo(1);
    assertThat(room.get("config").get("categoryQuotas").get("foreign-cap").asInt()).isEqualTo(4);
    assertThat(room.get("config").get("currencyCode").asText()).isEqualTo("EUR");
    assertThat(room.get("packLabels").get("roles")).hasSize(4);
    String code = issueInvite(commissioner, roomId);
    Person ann = joinNew(roomId, code, "sky");
    Person ben = joinNew(roomId, code, "teal");
    assertThat(get(ann, "/api/rooms/" + roomId).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            post(commissioner, "/api/rooms/" + roomId + "/transitions", Map.of("to", "retention"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(ben).isNotNull();
  }

  @Test
  void configValidationUsesTheStubPacksOwnIds() {
    Person commissioner = person();
    String roomId = createRoom(commissioner, "stub-football", "standard");
    var config =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            body(get(commissioner, "/api/rooms/" + roomId)).get("config").deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) config.get("roleMinimums")).put("BAT", 1);

    var response =
        put(
            commissioner,
            "/api/rooms/" + roomId + "/config",
            Map.of("version", 1, "config", config));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body(response).get("violations").findValuesAsText("field"))
        .contains("roleMinimums.BAT");
  }

  @Test
  void theRoomKeepsWorkingFromItsSnapshotWhenItsPackIsGone() {
    Person commissioner = person();
    String roomId = createRoom(commissioner, "stub-football", "standard");
    jdbc.update("UPDATE rooms SET pack_id = 'gone-pack' WHERE id = ?", UUID.fromString(roomId));

    var response = get(commissioner, "/api/rooms/" + roomId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(body(response).get("packLabels").get("packId").asText()).isEqualTo("stub-football");
  }
}
