package com.paddleup.accounts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paddleup.support.AbstractApiIT;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

/** Errors on public endpoints reach an anonymous caller with their own status, never as 401. */
@AutoConfigureMockMvc
class AnonymousErrorIT extends AbstractApiIT {

  @Autowired MockMvc mvc;

  private HttpStatus post(String contentType, String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(contentType));
    return HttpStatus.valueOf(
        rest.exchange(
                "/api/auth/email/start",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class)
            .getStatusCode()
            .value());
  }

  @Test
  void malformedJsonBodyIs400() {
    assertThat(post("application/json", "{not json")).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void unsupportedContentTypeIs415() {
    assertThat(post("text/plain", "hello")).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

  @Test
  void aCallerWhoIsAuthenticatedButNotAnAccountGets401FromAnyModulesController() throws Exception {
    var auth = new UsernamePasswordAuthenticationToken("someone", null, List.of());

    mvc.perform(get("/api/probe/whoami").with(authentication(auth)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorised"));
  }
}
