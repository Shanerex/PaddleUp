package com.paddleup.accounts.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.support.AbstractPostgresIT;
import com.paddleup.support.CapturingEmailSender;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** HTTP view of the sign-in rate limits: 429 with Retry-After, nothing minted past the limit. */
@Import(AuthThrottleIT.Capture.class)
@TestPropertySource(
    properties = {
      "paddleup.auth.throttle.start-per-email=3",
      "paddleup.auth.throttle.start-per-ip=4",
      "paddleup.auth.throttle.failed-codes-per-email=5",
      "paddleup.auth.throttle.failed-per-ip=6",
      "paddleup.auth.throttle.trusted-proxy-hops=1"
    })
class AuthThrottleIT extends AbstractPostgresIT {

  @TestConfiguration
  static class Capture {
    @Bean
    @Primary
    CapturingEmailSender capturingEmailSender() {
      return new CapturingEmailSender();
    }
  }

  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;
  @Autowired CapturingEmailSender mail;

  private static String email() {
    return "http-throttle-" + UUID.randomUUID() + "@example.test";
  }

  private ResponseEntity<String> post(String path, Map<String, String> body, String clientIp) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add("X-Forwarded-For", clientIp);
    return rest.postForEntity(path, new HttpEntity<>(body, headers), String.class);
  }

  private static String freshIp() {
    return "192.0.2." + (1 + (int) (Math.random() * 250)) + "-" + UUID.randomUUID();
  }

  @Test
  void fourthStartForOneEmailIs429WithRetryAfterAndMintsNoToken() {
    String email = email();
    for (int i = 0; i < 3; i++) {
      assertThat(post("/api/auth/email/start", Map.of("email", email), freshIp()).getStatusCode())
          .isEqualTo(HttpStatus.ACCEPTED);
    }

    var blocked = post("/api/auth/email/start", Map.of("email", email), freshIp());

    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(blocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).matches("\\d+");
    assertThat(blocked.getBody()).contains("too_many_requests");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM login_tokens WHERE email = ?", Integer.class, email))
        .isEqualTo(3);
  }

  @Test
  void startIsLimitedPerForwardedClientAddress() {
    String ip = freshIp();
    for (int i = 0; i < 4; i++) {
      assertThat(post("/api/auth/email/start", Map.of("email", email()), ip).getStatusCode())
          .isEqualTo(HttpStatus.ACCEPTED);
    }

    assertThat(post("/api/auth/email/start", Map.of("email", email()), ip).getStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void codeGuessingLocksAfterFiveMissesEvenAcrossNewStarts() {
    String email = email();
    post("/api/auth/email/start", Map.of("email", email), freshIp());
    String code = mail.codeTo(email);
    String wrong = code.equals("000000") ? "111111" : "000000";
    for (int i = 0; i < 5; i++) {
      assertThat(
              post("/api/auth/email/verify", Map.of("email", email, "code", wrong), freshIp())
                  .getStatusCode())
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    post("/api/auth/email/start", Map.of("email", email), freshIp());

    var locked =
        post(
            "/api/auth/email/verify",
            Map.of("email", email, "code", mail.codeTo(email)),
            freshIp());

    assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(locked.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
  }

  @Test
  void verifyFailuresFromOneAddressGive429() {
    String ip = freshIp();
    for (int i = 0; i < 6; i++) {
      assertThat(post("/api/auth/email/verify", Map.of("token", "bad-" + i), ip).getStatusCode())
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    var blocked = post("/api/auth/email/verify", Map.of("token", "bad-again"), ip);

    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(blocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).matches("\\d+");
  }
}
