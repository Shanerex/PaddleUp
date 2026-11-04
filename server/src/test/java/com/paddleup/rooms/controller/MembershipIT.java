package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.rooms.AbstractRoomsIT;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * AC-16: a non-member gets 404 on every room endpoint, never 403; a member who is not the
 * Commissioner gets 403 on Commissioner actions.
 */
class MembershipIT extends AbstractRoomsIT {

  /** One endpoint: its method, path under the room, and whether only the Commissioner may call. */
  record Endpoint(HttpMethod method, String path, boolean commissionerOnly) {
    @Override
    public String toString() {
      return method + " " + path;
    }
  }

  static Stream<Endpoint> endpoints() {
    return Stream.of(
        new Endpoint(HttpMethod.GET, "", false),
        new Endpoint(HttpMethod.GET, "/members", false),
        new Endpoint(HttpMethod.GET, "/transitions", false),
        new Endpoint(HttpMethod.POST, "/franchises", true),
        new Endpoint(HttpMethod.POST, "/commissioner", true),
        new Endpoint(HttpMethod.POST, "/transitions", true),
        new Endpoint(HttpMethod.PUT, "/config", true),
        new Endpoint(HttpMethod.GET, "/members", false),
        new Endpoint(HttpMethod.GET, "/transitions", false),
        new Endpoint(HttpMethod.POST, "/franchises", true),
        new Endpoint(HttpMethod.POST, "/commissioner", true),
        new Endpoint(HttpMethod.POST, "/transitions", true),
        new Endpoint(HttpMethod.PUT, "/config", true),
        new Endpoint(HttpMethod.GET, "/invite", true),
        new Endpoint(HttpMethod.POST, "/invite", true),
        new Endpoint(HttpMethod.DELETE, "/invite", true));
  }

  private ResponseEntity<String> send(Person who, String roomId, Endpoint e, Object body) {
    return call(e.method(), "/api/rooms/" + roomId + e.path(), who.cookie(), body);
  }

  @Test
  void aNonMemberGets404OnEveryEndpoint() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    Person stranger = person();

    for (Endpoint e : (Iterable<Endpoint>) endpoints()::iterator) {
      var response = send(stranger, roomId, e, java.util.Map.of());
      assertThat(response.getStatusCode()).as(e.toString()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody()).as(e.toString()).contains("room_not_found");
    }
  }

  @Test
  void anUnknownRoomLooksTheSameAsSomeoneElsesRoom() {
    var unknown = get(person(), "/api/rooms/" + UUID.randomUUID());
    var notAUuid = get(person(), "/api/rooms/not-a-uuid");

    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(notAUuid.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(unknown.getBody()).isEqualTo(notAUuid.getBody());
  }

  @Test
  void aManagerGets403OnEveryCommissionerEndpointAndCanReadTheRoom() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    Person manager = insertManager(roomId, "crimson");

    for (Endpoint e : (Iterable<Endpoint>) endpoints()::iterator) {
      if (!e.commissionerOnly()) {
        continue;
      }
      var response = send(manager, roomId, e, java.util.Map.of());
      assertThat(response.getStatusCode()).as(e.toString()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(send(manager, roomId, new Endpoint(HttpMethod.GET, "", false), null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(body(get(manager, "/api/rooms/" + roomId)).get("myRole").asText())
        .isEqualTo("manager");
  }

  @Test
  void signedOutCallersGet401() {
    String roomId = createRoom(person());

    assertThat(get(null, "/api/rooms/" + roomId).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
