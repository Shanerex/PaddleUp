package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.rooms.AbstractRoomsIT;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;

/**
 * AC-18: the stub football pack runs import, copy, edit, reset and set order, zero core changes.
 */
@TestPropertySource(
    properties = {
      "paddleup.packs.dir=src/test/resources/packs/stub",
      "paddleup.admin.emails=stub-admin@example.test"
    })
class StubPackPoolIT extends AbstractRoomsIT {

  private static final String CSV =
      """
      name,country,role,tags,base_price,set,provider_id
      Ana Gil,Spain,gk,local,500000,Keepers,
      Bo Dan,Brazil,def,foreign,1000000,Defence,
      Cy Eli,Spain,mid,local;foreign,5250000,Midfield,f-9
      Di Fox,Chile,fwd,foreign,300000,Attack,
      Bad Role,Chile,BAT,local,500000,Attack,
      Bad Price,Chile,fwd,local,150000,Attack,
      """;

  @Test
  void importCopyEditResetAndSetOrderAllWorkOnTheSecondPack() throws Exception {
    String adminCookie = signIn("stub-admin@example.test");
    var parts = new LinkedMultiValueMap<String, Object>();
    parts.add("packId", "stub-football");
    parts.add(
        "file",
        new ByteArrayResource(CSV.getBytes(StandardCharsets.UTF_8)) {
          @Override
          public String getFilename() {
            return "football.csv";
          }
        });
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    headers.add(HttpHeaders.COOKIE, adminCookie);
    var imported =
        rest.exchange(
            "/api/admin/datasets", HttpMethod.POST, new HttpEntity<>(parts, headers), String.class);
    assertThat(imported.getStatusCode()).as(imported.getBody()).isEqualTo(HttpStatus.CREATED);
    assertThat(body(imported).get("imported").asInt()).isEqualTo(4);
    assertThat(body(imported).get("rows").findValuesAsText("field"))
        .containsExactly("role", "base_price");

    Person commissioner = person();
    String roomId = createRoom(commissioner, "stub-football", "standard");
    String pool = "/api/rooms/" + roomId + "/pool";
    JsonNode copy = body(get(commissioner, pool));
    assertThat(copy.get("players").findValuesAsText("name"))
        .containsExactly("Ana Gil", "Bo Dan", "Cy Eli", "Di Fox");
    assertThat(copy.get("sets").findValuesAsText("name"))
        .containsExactly("Keepers", "Defence", "Midfield", "Attack");
    int v = copy.get("version").asInt();

    var player = new LinkedHashMap<String, Object>();
    player.put("version", v);
    player.put("name", "Ed Gus");
    player.put("country", "Peru");
    player.put("role", "def");
    player.put("tags", List.of("local"));
    player.put("basePrice", 2500000);
    player.put("set", "Defence");
    assertThat(post(commissioner, pool + "/players", player).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    player.put("version", v + 1);
    player.put("role", "BAT");
    var rejected = post(commissioner, pool + "/players", player);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(body(rejected).get("violations").findValuesAsText("message").get(0))
        .contains("Stub Football");

    var order = List.of("Attack", "Midfield", "Defence", "Keepers");
    assertThat(
            put(commissioner, pool + "/sets", Map.of("version", v + 1, "order", order))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(body(get(commissioner, pool)).get("sets").findValuesAsText("name"))
        .containsExactlyElementsOf(order);

    assertThat(post(commissioner, pool + "/reset", Map.of("version", v + 2)).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    JsonNode reset = body(get(commissioner, pool));
    assertThat(reset.get("players")).hasSize(4);
    assertThat(reset.get("sets").findValuesAsText("name"))
        .containsExactly("Keepers", "Defence", "Midfield", "Attack");
    assertThat(reset.get("editCount").asInt()).isZero();
  }
}
