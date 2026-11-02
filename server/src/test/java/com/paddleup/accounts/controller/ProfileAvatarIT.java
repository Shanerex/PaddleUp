package com.paddleup.accounts.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.accounts.service.GoogleProfile;
import com.paddleup.accounts.service.GoogleSignInService;
import com.paddleup.support.AbstractApiIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

class ProfileAvatarIT extends AbstractApiIT {

  private final ObjectMapper json = new ObjectMapper();

  @Autowired GoogleSignInService google;

  private HttpStatus patchAvatar(String cookie, String avatar) {
    return HttpStatus.valueOf(
        call(HttpMethod.PATCH, "/api/me", cookie, Map.of("avatar", avatar))
            .getStatusCode()
            .value());
  }

  private JsonNode me(String cookie) throws Exception {
    return json.readTree(call(HttpMethod.GET, "/api/me", cookie, null).getBody());
  }

  @Test
  void presetPersists() throws Exception {
    String cookie = signIn(newEmail());

    assertThat(patchAvatar(cookie, "preset-07")).isEqualTo(HttpStatus.OK);

    assertThat(me(cookie).get("avatar").asText()).isEqualTo("preset-07");
  }

  @Test
  void storedGooglePictureUrlPersists() throws Exception {
    String email = newEmail();
    String cookie = signIn(email);
    String picture = "https://example.test/me-" + UUID.randomUUID() + ".png";
    google.signIn(new GoogleProfile(UUID.randomUUID().toString(), email, true, "Gee", picture));

    assertThat(patchAvatar(cookie, picture)).isEqualTo(HttpStatus.OK);

    assertThat(me(cookie).get("avatar").asText()).isEqualTo(picture);
    assertThat(me(cookie).get("googlePictureUrl").asText()).isEqualTo(picture);
  }

  @Test
  void anyOtherValueIsRejected() throws Exception {
    String email = newEmail();
    String cookie = signIn(email);
    String othersPicture = "https://example.test/other-" + UUID.randomUUID() + ".png";
    google.signIn(
        new GoogleProfile(UUID.randomUUID().toString(), newEmail(), true, "Other", othersPicture));

    for (String bad :
        new String[] {
          "preset-00",
          "preset-13",
          "preset-1",
          "PRESET-01",
          "",
          "https://evil.test/x.png",
          "javascript:alert(1)",
          othersPicture,
          "preset-01 "
        }) {
      assertThat(patchAvatar(cookie, bad)).as(bad).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    assertThat(me(cookie).get("avatar").asText()).isEqualTo("preset-01");
  }

  @Test
  void avatarAndNameChangeTogetherOrNotAtAll() throws Exception {
    String cookie = signIn(newEmail());
    String before = me(cookie).get("displayName").asText();

    var response =
        call(
            HttpMethod.PATCH,
            "/api/me",
            cookie,
            Map.of("displayName", "New Name", "avatar", "bad"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(me(cookie).get("displayName").asText()).isEqualTo(before);

    var ok =
        call(
            HttpMethod.PATCH,
            "/api/me",
            cookie,
            Map.of("displayName", "New Name", "avatar", "preset-03"));
    assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me(cookie).get("avatar").asText()).isEqualTo("preset-03");
  }

  @Test
  void listsThePresets() throws Exception {
    String cookie = signIn(newEmail());

    JsonNode body = json.readTree(call(HttpMethod.GET, "/api/avatars", cookie, null).getBody());

    assertThat(body.get("presets")).hasSize(12);
    assertThat(body.get("presets").get(0).asText()).isEqualTo("preset-01");
    assertThat(body.get("presets").get(11).asText()).isEqualTo("preset-12");
    assertThat(call(HttpMethod.GET, "/api/avatars", null, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
