package com.paddleup.support;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Integration test over real HTTP with a captured email sender and a sign-in helper. */
@Import(AbstractApiIT.Capture.class)
public abstract class AbstractApiIT extends AbstractPostgresIT {

  @TestConfiguration
  static class Capture {
    @Bean
    @Primary
    CapturingEmailSender capturingEmailSender() {
      return new CapturingEmailSender();
    }
  }

  @Autowired protected TestRestTemplate rest;
  @Autowired protected CapturingEmailSender mail;

  protected static String newEmail() {
    return "api-" + UUID.randomUUID() + "@example.test";
  }

  /** Signs a new or existing email in by link and returns a Cookie header value. */
  protected String signIn(String email) {
    rest.postForEntity("/api/auth/email/start", Map.of("email", email), String.class);
    var verify =
        rest.postForEntity(
            "/api/auth/email/verify", Map.of("token", mail.tokenTo(email)), String.class);
    String setCookie =
        verify.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
            .filter(c -> c.startsWith("pu_session="))
            .findFirst()
            .orElseThrow();
    return setCookie.substring(0, setCookie.indexOf(';'));
  }

  protected ResponseEntity<String> call(
      HttpMethod method, String url, String cookie, Object jsonBody) {
    HttpHeaders headers = new HttpHeaders();
    if (cookie != null) headers.add(HttpHeaders.COOKIE, cookie);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return rest.exchange(url, method, new HttpEntity<>(jsonBody, headers), String.class);
  }
}
