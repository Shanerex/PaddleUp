package com.paddleup.accounts.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.support.AbstractApiIT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

class ProfileNameIT extends AbstractApiIT {

  private final ObjectMapper json = new ObjectMapper();

  private HttpStatus patchName(String cookie, String name) {
    return HttpStatus.valueOf(
        call(HttpMethod.PATCH, "/api/me", cookie, Map.of("displayName", name))
            .getStatusCode()
            .value());
  }

  private String nameOnNextRead(String cookie) throws Exception {
    JsonNode me = json.readTree(call(HttpMethod.GET, "/api/me", cookie, null).getBody());
    return me.get("displayName").asText();
  }

  @Test
  void twoAndThirtyCharactersPersistOneAndThirtyOneAreRejected() throws Exception {
    String cookie = signIn(newEmail());

    assertThat(patchName(cookie, "Al")).isEqualTo(HttpStatus.OK);
    assertThat(nameOnNextRead(cookie)).isEqualTo("Al");

    String thirty = "n".repeat(30);
    assertThat(patchName(cookie, thirty)).isEqualTo(HttpStatus.OK);
    assertThat(nameOnNextRead(cookie)).isEqualTo(thirty);

    assertThat(patchName(cookie, "x")).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(patchName(cookie, "n".repeat(31))).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(nameOnNextRead(cookie)).isEqualTo(thirty);
  }

  @Test
  void twoUsersMayShareAName() throws Exception {
    String a = signIn(newEmail());
    String b = signIn(newEmail());

    assertThat(patchName(a, "Sam Same")).isEqualTo(HttpStatus.OK);
    assertThat(patchName(b, "Sam Same")).isEqualTo(HttpStatus.OK);

    assertThat(nameOnNextRead(a)).isEqualTo("Sam Same");
    assertThat(nameOnNextRead(b)).isEqualTo("Sam Same");
  }

  @Test
  void markupAndControlCharactersAreStoredSanitised() throws Exception {
    String cookie = signIn(newEmail());

    assertThat(patchName(cookie, "<b>Ann</b><script>alert(1)</script>\u0007 Lee"))
        .isEqualTo(HttpStatus.OK);

    assertThat(nameOnNextRead(cookie)).isEqualTo("Ann Lee");
  }

  @Test
  void nameThatIsEmptyAfterSanitisingIsRejected() {
    String cookie = signIn(newEmail());

    assertThat(patchName(cookie, "<b></b>")).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void patchNeedsASession() {
    var response = call(HttpMethod.PATCH, "/api/me", null, Map.of("displayName", "Nobody"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void emptyPatchIsRejected() {
    String cookie = signIn(newEmail());

    assertThat(call(HttpMethod.PATCH, "/api/me", cookie, Map.of()).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
