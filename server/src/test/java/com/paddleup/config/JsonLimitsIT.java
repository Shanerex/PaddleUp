package com.paddleup.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.support.AbstractApiIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The JSON read limits are app-wide: a deep, long or oversize body is a 400 on an accounts endpoint
 * and on a rooms endpoint alike, never a 500.
 */
class JsonLimitsIT extends AbstractApiIT {

  /**
   * Jackson checks the document length as it refills its read buffer, so a body is refused once it
   * is a buffer or so past the limit; this is well past.
   */
  private static final int OVERSIZE = (int) JsonLimitsConfig.MAX_DOCUMENT_LENGTH + 64 * 1024;

  @Autowired ObjectMapper mapper;

  private ResponseEntity<String> post(String url, String cookie, String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (cookie != null) headers.add(HttpHeaders.COOKIE, cookie);
    return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  /** A valid start-sign-in body with one extra, ignored property holding {@code extra}. */
  private static String startBody(String extra) {
    return "{\"email\":\"" + newEmail() + "\",\"extra\":" + extra + "}";
  }

  private static String nested(int depth) {
    return "[".repeat(depth) + "]".repeat(depth);
  }

  private static String manySmallStrings(int bytes) {
    return "[" + "\"ab\",".repeat(bytes / 5) + "\"ab\"]";
  }

  @Test
  void aBodyWithinTheLimitsIsAccepted() {
    var response =
        post(
            "/api/auth/email/start",
            null,
            startBody(nested(JsonLimitsConfig.MAX_NESTING_DEPTH - 2)));
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
  }

  @Test
  void aTooDeepBodyIs400OnAnAccountsEndpoint() {
    var response =
        post(
            "/api/auth/email/start",
            null,
            startBody(nested(JsonLimitsConfig.MAX_NESTING_DEPTH + 10)));
    assertThat(response.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void theSharedMapperRefusesATooLongString() throws Exception {
    String tooLong = "\"" + "a".repeat(JsonLimitsConfig.MAX_STRING_LENGTH + 1) + "\"";
    String longest = "\"" + "a".repeat(JsonLimitsConfig.MAX_STRING_LENGTH) + "\"";
    assertThatThrownBy(() -> mapper.readValue(tooLong, String.class))
        .isInstanceOf(StreamConstraintsException.class);
    assertThat(mapper.readValue(longest, String.class)).hasSize(JsonLimitsConfig.MAX_STRING_LENGTH);
  }

  @Test
  void aTooLongStringIs400OnAnAccountsEndpoint() {
    String email = "a".repeat(JsonLimitsConfig.MAX_STRING_LENGTH) + "@example.test";
    var response = post("/api/auth/email/start", null, "{\"email\":\"" + email + "\"}");
    assertThat(response.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void anOversizeDocumentIs400OnAnAccountsEndpoint() {
    var response = post("/api/auth/email/start", null, startBody(manySmallStrings(OVERSIZE)));
    assertThat(response.getStatusCode().value()).isEqualTo(400);
  }

  private static String roomBody(String extra) {
    return "{\"packId\":\"cricket-t20\",\"presetId\":\"standard\",\"name\":\"Friends\","
        + "\"extra\":"
        + extra
        + "}";
  }

  @Test
  void theSameLimitsApplyToARoomsEndpoint() {
    String cookie = signIn(newEmail());
    assertThat(post("/api/rooms", cookie, roomBody("[]")).getStatusCode().value()).isEqualTo(201);
    assertThat(
            post("/api/rooms", cookie, roomBody(nested(JsonLimitsConfig.MAX_NESTING_DEPTH + 10)))
                .getStatusCode()
                .value())
        .isEqualTo(400);
    assertThat(
            post("/api/rooms", cookie, roomBody(manySmallStrings(OVERSIZE)))
                .getStatusCode()
                .value())
        .isEqualTo(400);
  }
}
