package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.rooms.AbstractRoomsIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-9, AC-13: the Commissioner reorders the sets; every member reads the new order. */
class PoolSetOrderIT extends AbstractRoomsIT {

  Person commissioner;
  String roomId;

  @BeforeEach
  void room() {
    importSeason("cricket-t20", SEASON_CSV);
    commissioner = person();
    roomId = createRoom(commissioner);
  }

  private String sets() {
    return "/api/rooms/" + roomId + "/pool/sets";
  }

  private JsonNode read(Person who) {
    return body(get(who, "/api/rooms/" + roomId + "/pool"));
  }

  @Test
  void theOrderPersistsAndEveryMemberReadsIt() {
    Person manager = insertManager(roomId, "sky");
    JsonNode before = read(commissioner);
    int v = before.get("version").asInt();
    List<String> order = List.of("Uncapped", "Marquee", "Capped Batters");

    var response = put(commissioner, sets(), Map.of("version", v, "order", order));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    for (Person who : List.of(commissioner, manager)) {
      JsonNode pool = read(who);
      assertThat(pool.get("sets").findValuesAsText("name")).containsExactlyElementsOf(order);
      assertThat(pool.get("version").asInt()).isEqualTo(v + 1);
    }
    assertThat(read(commissioner).get("sets").get(0).get("players").asInt()).isEqualTo(2);
    assertThat(read(commissioner).get("players")).hasSize(6);
  }

  @Test
  void aMissingDuplicateOrUnknownSetIs422AndTheOrderIsUnchanged() {
    JsonNode before = read(commissioner);
    int v = before.get("version").asInt();
    List<List<String>> bad =
        List.of(
            List.of("Marquee", "Capped Batters"),
            List.of("Marquee", "Marquee", "Capped Batters"),
            List.of("Marquee", "Capped Batters", "Nope"),
            List.of("Marquee", "Capped Batters", "Uncapped", "Marquee"),
            List.of());

    for (List<String> order : bad) {
      var response = put(commissioner, sets(), Map.of("version", v, "order", order));
      assertThat(response.getStatusCode())
          .as(order.toString())
          .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
      assertThat(body(response).get("violations").findValuesAsText("field"))
          .containsExactly("order");
    }
    assertThat(read(commissioner)).isEqualTo(before);
  }

  @Test
  void aMemberGets403AndAMissingOrderIs400() {
    Person manager = insertManager(roomId, "sky");
    int v = read(commissioner).get("version").asInt();
    List<String> order = List.of("Uncapped", "Marquee", "Capped Batters");

    assertThat(put(manager, sets(), Map.of("version", v, "order", order)).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(put(commissioner, sets(), Map.of("version", v)).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(read(commissioner).get("sets").get(0).get("name").asText()).isEqualTo("Marquee");
  }
}
