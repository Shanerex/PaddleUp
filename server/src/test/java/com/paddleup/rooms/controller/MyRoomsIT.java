package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.rooms.AbstractRoomsIT;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** GET /api/rooms: the home hub lists only the rooms the caller is in. */
class MyRoomsIT extends AbstractRoomsIT {

  private JsonNode mine(Person who) {
    var response = get(who, "/api/rooms");
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    return body(response);
  }

  private JsonNode byId(JsonNode list, String id) {
    return StreamSupport.stream(list.spliterator(), false)
        .filter(n -> n.get("id").asText().equals(id))
        .findFirst()
        .orElse(null);
  }

  @Test
  void aNewUserHasNoRooms() {
    assertThat(mine(person())).isEmpty();
  }

  @Test
  void adminOnlyCommissionerSeesTheRoomWithNoFranchise() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);

    JsonNode room = byId(mine(commissioner), roomId);

    assertThat(room).isNotNull();
    assertThat(room.get("state").asText()).isEqualTo("setup");
    assertThat(room.get("commissioner").asBoolean()).isTrue();
    assertThat(room.get("franchiseCount").asInt()).isZero();
    assertThat(room.get("minManagers").asInt()).isEqualTo(2);
    assertThat(room.get("maxManagers").asInt()).isEqualTo(10);
    assertThat(room.get("packName").asText()).isEqualTo("Cricket T20");
    assertThat(room.get("myFranchise").isNull()).isTrue();
  }

  @Test
  void managerSeesTheirFranchiseAndOutsidersSeeNothing() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person ann = joinNew(roomId, code, "sky");
    Person outsider = person();

    JsonNode room = byId(mine(ann), roomId);

    assertThat(room.get("commissioner").asBoolean()).isFalse();
    assertThat(room.get("franchiseCount").asInt()).isEqualTo(1);
    assertThat(room.get("myFranchise").get("colour").asText()).isEqualTo("sky");
    assertThat(byId(mine(outsider), roomId)).isNull();
  }

  @Test
  void removedManagerNoLongerSeesTheRoom() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person ann = joinNew(roomId, code, "sky");
    String franchiseId =
        jdbc.queryForObject(
            "SELECT id::text FROM franchises WHERE room_id = ?",
            String.class,
            UUID.fromString(roomId));

    delete(commissioner, "/api/rooms/" + roomId + "/franchises/" + franchiseId);

    assertThat(byId(mine(ann), roomId)).isNull();
  }

  @Test
  void newestRoomComesFirst() {
    Person commissioner = person();
    String first = createRoom(commissioner);
    String second = createRoom(commissioner);
    put(
        commissioner,
        "/api/rooms/" + first + "/config",
        Map.of(
            "version", configVersion(commissioner, first), "config", config(commissioner, first)));

    JsonNode list = mine(commissioner);

    assertThat(list.get(0).get("id").asText()).isEqualTo(first);
    assertThat(list.get(1).get("id").asText()).isEqualTo(second);
  }

  @Test
  void signedOutCallerGets401() {
    assertThat(get(null, "/api/rooms").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private JsonNode config(Person who, String roomId) {
    return body(get(who, "/api/rooms/" + roomId)).get("config");
  }
}
