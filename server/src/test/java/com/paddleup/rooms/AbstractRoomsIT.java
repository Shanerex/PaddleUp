package com.paddleup.rooms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.support.AbstractApiIT;
import com.paddleup.support.MutableClock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Shared helpers for rooms integration tests over real HTTP. */
@Import(AbstractRoomsIT.Clocks.class)
public abstract class AbstractRoomsIT extends AbstractApiIT {

  @TestConfiguration
  static class Clocks {
    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock();
    }
  }

  /** A signed-in test user. */
  public record Person(String email, String cookie) {}

  /** A franchise body that is valid and different on every call. */
  public static Map<String, String> franchiseBody(String colour) {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    return Map.of("name", "Team " + tag, "colour", colour, "monogram", "TM");
  }

  @Autowired protected ObjectMapper json;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected MutableClock clock;

  @AfterEach
  void realTime() {
    clock.reset();
  }

  protected Person person() {
    String email = newEmail();
    return new Person(email, signIn(email));
  }

  protected JsonNode body(ResponseEntity<String> response) {
    try {
      return json.readTree(response.getBody());
    } catch (Exception e) {
      throw new AssertionError("not json: " + response.getBody(), e);
    }
  }

  protected ResponseEntity<String> get(Person who, String url) {
    return call(HttpMethod.GET, url, who == null ? null : who.cookie(), null);
  }

  protected ResponseEntity<String> post(Person who, String url, Object body) {
    return call(HttpMethod.POST, url, who == null ? null : who.cookie(), body);
  }

  protected ResponseEntity<String> put(Person who, String url, Object body) {
    return call(HttpMethod.PUT, url, who.cookie(), body);
  }

  protected ResponseEntity<String> patch(Person who, String url, Object body) {
    return call(HttpMethod.PATCH, url, who.cookie(), body);
  }

  protected ResponseEntity<String> delete(Person who, String url) {
    return call(HttpMethod.DELETE, url, who.cookie(), null);
  }

  protected UUID userId(Person who) {
    return UUID.fromString(body(get(who, "/api/me")).get("id").asText());
  }

  /** Creates a room from the Cricket T20 standard preset and returns its id. */
  protected String createRoom(Person commissioner) {
    return createRoom(commissioner, "cricket-t20", "standard");
  }

  protected String createRoom(Person commissioner, String packId, String presetId) {
    var response =
        post(
            commissioner,
            "/api/rooms",
            Map.of(
                "packId",
                packId,
                "presetId",
                presetId,
                "name",
                "Room " + UUID.randomUUID().toString().substring(0, 8)));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return body(response).get("id").asText();
  }

  /** The room's current config version. */
  protected int configVersion(Person who, String roomId) {
    return body(get(who, "/api/rooms/" + roomId)).get("configVersion").asInt();
  }

  protected String issueInvite(Person commissioner, String roomId) {
    var response = post(commissioner, "/api/rooms/" + roomId + "/invite", null);
    assertThat(response.getStatusCode().is2xxSuccessful()).as(response.getBody()).isTrue();
    return body(response).get("code").asText();
  }

  /** Redeems a code with a fresh valid franchise body; returns the raw response. */
  protected ResponseEntity<String> join(Person who, String code, String colour) {
    return post(who, "/api/join", Map.of("code", code, "franchise", franchiseBody(colour)));
  }

  /** Joins as a new person on the next free colour; returns that person. */
  protected Person joinNew(String roomId, String code, String colour) {
    Person p = person();
    var response = join(p, code, colour);
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    return p;
  }

  /** Adds a manager straight into the database, for tests that do not exercise joining. */
  protected Person insertManager(String roomId, String colour) {
    Person p = person();
    jdbc.update(
        "INSERT INTO franchises (id, room_id, owner_user_id, name, colour, monogram, created_at,"
            + " updated_at) VALUES (?, ?, ?, ?, ?, 'TM', now(), now())",
        UUID.randomUUID(),
        UUID.fromString(roomId),
        userId(p),
        "Team " + UUID.randomUUID().toString().substring(0, 8),
        colour);
    return p;
  }

  protected static final List<String> COLOURS =
      List.of(
          "crimson",
          "tangerine",
          "bronze",
          "olive",
          "fern",
          "emerald",
          "teal",
          "sky",
          "cobalt",
          "iris",
          "orchid",
          "rose");
}
