package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.rooms.AbstractRoomsIT;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/** AC-9: failed redeems are limited per user and per client address, before the code lookup. */
@TestPropertySource(
    properties = {
      "paddleup.rooms.join-throttle.failed-per-user=3",
      "paddleup.rooms.join-throttle.failed-per-ip=5",
      "paddleup.rooms.join-throttle.window=1m",
      "paddleup.auth.throttle.trusted-proxy-hops=1"
    })
class JoinThrottleIT extends AbstractRoomsIT {

  private static final String BAD = "ZZZZZZZZZZZZ";

  private static String freshIp() {
    return "198.51.100." + (1 + (int) (Math.random() * 250)) + "-" + UUID.randomUUID();
  }

  private ResponseEntity<String> redeem(Person who, String code, String ip, String colour) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, who.cookie());
    headers.add("X-Forwarded-For", ip);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return rest.exchange(
        "/api/join",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("code", code, "franchise", franchiseBody(colour)), headers),
        String.class);
  }

  private ResponseEntity<String> preview(Person who, String code, String ip) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, who.cookie());
    headers.add("X-Forwarded-For", ip);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return rest.exchange(
        "/api/join/preview",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("code", code), headers),
        String.class);
  }

  private int franchises(String roomId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM franchises WHERE room_id = ?",
        Integer.class,
        UUID.fromString(roomId));
  }

  @Test
  void aUserOverTheirFailedAttemptsGets429EvenWithAValidCode() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person ann = person();
    for (int i = 0; i < 3; i++) {
      assertThat(redeem(ann, BAD, freshIp(), "sky").getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
    }

    var blocked = redeem(ann, code, freshIp(), "sky");

    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(blocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).matches("\\d+");
    assertThat(body(blocked).get("error").asText()).isEqualTo("too_many_requests");
    assertThat(franchises(roomId)).isZero();
    assertThat(preview(ann, code, freshIp()).getStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void otherUsersAreNotAffectedByOneUsersFailures() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person ann = person();
    for (int i = 0; i < 3; i++) {
      redeem(ann, BAD, freshIp(), "sky");
    }

    assertThat(redeem(person(), code, freshIp(), "sky").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void anAddressOverItsFailedAttemptsIsBlockedForEveryUser() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    String ip = freshIp();
    for (int i = 0; i < 5; i++) {
      assertThat(redeem(person(), BAD, ip, "sky").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    var blocked = redeem(person(), code, ip, "sky");
    var elsewhere = redeem(person(), code, freshIp(), "sky");

    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(elsewhere.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void successfulRedeemsAreNotCounted() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person ann = person();
    String ip = freshIp();

    for (int i = 0; i < 10; i++) {
      assertThat(redeem(ann, code, ip, "sky").getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }

  @Test
  void aRoomFullOrBadFieldsAnswerIsNotAFailedCodeAttempt() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    Person ann = person();
    String ip = freshIp();

    for (int i = 0; i < 6; i++) {
      var bad =
          rest.exchange(
              "/api/join",
              HttpMethod.POST,
              new HttpEntity<>(
                  Map.of(
                      "code",
                      code,
                      "franchise",
                      Map.of("name", "A", "colour", "sky", "monogram", "A")),
                  headers(ann, ip)),
              String.class);
      assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    assertThat(redeem(ann, code, ip, "sky").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private HttpHeaders headers(Person who, String ip) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, who.cookie());
    headers.add("X-Forwarded-For", ip);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  @Test
  void theLimitLiftsOnceTheWindowHasPassed() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    Person ann = person();
    Instant start = Instant.parse("2031-05-01T09:00:00Z");
    clock.freeze(start);
    String code = issueInvite(commissioner, roomId);
    for (int i = 0; i < 3; i++) {
      redeem(ann, BAD, freshIp(), "sky");
    }
    assertThat(redeem(ann, code, freshIp(), "sky").getStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

    clock.freeze(start.plus(Duration.ofSeconds(61)));

    assertThat(redeem(ann, code, freshIp(), "sky").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void theThrottleStoresHashesNotUserIdsOrAddresses() {
    Person commissioner = person();
    createRoom(commissioner);
    Person ann = person();
    String ip = freshIp();
    redeem(ann, BAD, ip, "sky");

    var keys = jdbc.queryForList("SELECT key_hash FROM room_join_throttle_events", String.class);

    assertThat(keys).isNotEmpty().allMatch(k -> k.matches("[0-9a-f]{64}"));
    assertThat(keys).doesNotContain(userId(ann).toString(), ip);
  }
}
