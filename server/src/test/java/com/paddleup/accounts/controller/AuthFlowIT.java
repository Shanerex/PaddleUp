package com.paddleup.accounts.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.accounts.dto.ProfileDto;
import com.paddleup.support.AbstractPostgresIT;
import com.paddleup.support.CapturingEmailSender;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(AuthFlowIT.Capture.class)
class AuthFlowIT extends AbstractPostgresIT {

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
    return "flow-" + UUID.randomUUID() + "@example.test";
  }

  private int sessionRows() {
    return jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class);
  }

  private ResponseEntity<String> start(String email) {
    return rest.postForEntity("/api/auth/email/start", Map.of("email", email), String.class);
  }

  private static String sessionCookie(ResponseEntity<?> response) {
    return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
        .filter(c -> c.startsWith("pu_session="))
        .findFirst()
        .orElseThrow();
  }

  private static HttpHeaders withCookie(String setCookie) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, setCookie.substring(0, setCookie.indexOf(';')));
    return headers;
  }

  private ResponseEntity<String> exchange(HttpMethod method, String url, HttpHeaders headers) {
    return rest.exchange(url, method, new HttpEntity<>(headers), String.class);
  }

  private static String lastSessionCookie(ResponseEntity<?> response) {
    var cookies =
        response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, java.util.List.of()).stream()
            .filter(c -> c.startsWith("pu_session="))
            .toList();
    assertThat(cookies).isNotEmpty();
    return cookies.getLast();
  }

  private String sessionIdOf(String setCookie) {
    String value = setCookie.substring("pu_session=".length(), setCookie.indexOf(';'));
    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }

  @Test
  void newEmailRedeemsCodeAndReachesProtectedEndpoint() {
    String email = email();
    assertThat(start(email).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    var verify =
        rest.postForEntity(
            "/api/auth/email/verify",
            Map.of("email", email, "code", mail.codeTo(email)),
            ProfileDto.class);

    assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(verify.getBody().email()).isEqualTo(email);
    var me =
        rest.exchange(
            "/api/me",
            HttpMethod.GET,
            new HttpEntity<>(withCookie(sessionCookie(verify))),
            ProfileDto.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody().id()).isEqualTo(verify.getBody().id());
  }

  @Test
  void linkTokenAlsoSignsIn() {
    String email = email();
    start(email);

    var verify =
        rest.postForEntity(
            "/api/auth/email/verify", Map.of("token", mail.tokenTo(email)), ProfileDto.class);

    assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            exchange(HttpMethod.GET, "/api/me", withCookie(sessionCookie(verify))).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void sessionCookieIsHttpOnlyLaxAndBackedByARow() {
    String email = email();
    start(email);
    var verify =
        rest.postForEntity(
            "/api/auth/email/verify", Map.of("token", mail.tokenTo(email)), String.class);

    String cookie = sessionCookie(verify);

    assertThat(cookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/");
    assertThat(cookie).contains("Secure");
    assertThat(cookie).contains("Max-Age=" + 30 * 24 * 60 * 60);
    Integer timeout =
        jdbc.queryForObject(
            "SELECT max_inactive_interval FROM spring_session WHERE session_id = ?",
            Integer.class,
            sessionIdOf(cookie));
    assertThat(timeout).isEqualTo(30 * 24 * 60 * 60);
  }

  @Test
  void wrongExpiredAndReusedCredentialsGive401AndNoSession() {
    String email = email();
    start(email);
    String code = mail.codeTo(email);
    String wrong = code.equals("000000") ? "111111" : "000000";
    int before = sessionRows();

    var badCode =
        rest.postForEntity(
            "/api/auth/email/verify", Map.of("email", email, "code", wrong), String.class);
    var badLink =
        rest.postForEntity("/api/auth/email/verify", Map.of("token", "nope"), String.class);
    assertThat(badCode.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(badLink.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(badCode.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    assertThat(badLink.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();

    jdbc.update(
        "UPDATE login_tokens SET expires_at = now() - interval '1 second' WHERE email = ?", email);
    var expired =
        rest.postForEntity(
            "/api/auth/email/verify", Map.of("email", email, "code", code), String.class);
    assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    String email2 = email();
    start(email2);
    String token2 = mail.tokenTo(email2);
    assertThat(
            rest.postForEntity("/api/auth/email/verify", Map.of("token", token2), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    int afterOneGoodSignIn = sessionRows();
    var reused =
        rest.postForEntity("/api/auth/email/verify", Map.of("token", token2), String.class);
    assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(reused.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();

    assertThat(afterOneGoodSignIn).isEqualTo(before + 1);
    assertThat(sessionRows()).isEqualTo(afterOneGoodSignIn);
  }

  @Test
  void badRequestsAreRejected() {
    assertThat(
            rest.postForEntity(
                    "/api/auth/email/start", Map.of("email", "not-an-email"), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(rest.postForEntity("/api/auth/email/verify", Map.of(), String.class).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void protectedEndpointWithoutCookieIs401() {
    assertThat(rest.getForEntity("/api/me", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void signedInRequestRenewsTheCookieSoItSlidesWithTheSession() {
    String email = email();
    start(email);
    var verify =
        rest.postForEntity(
            "/api/auth/email/verify", Map.of("token", mail.tokenTo(email)), String.class);
    String cookie = sessionCookie(verify);

    var me = exchange(HttpMethod.GET, "/api/me", withCookie(cookie));

    String renewed = lastSessionCookie(me);
    assertThat(sessionIdOf(renewed)).isEqualTo(sessionIdOf(cookie));
    assertThat(renewed)
        .contains("Max-Age=" + 30 * 24 * 60 * 60)
        .contains("HttpOnly")
        .contains("SameSite=Lax");
  }

  @Test
  void requestWithoutAValidSessionGetsNoSessionCookie() {
    HttpHeaders stale = new HttpHeaders();
    stale.add(HttpHeaders.COOKIE, "pu_session=bm90LWEtc2Vzc2lvbg");

    for (var response :
        java.util.List.of(
            rest.getForEntity("/api/me", String.class),
            exchange(HttpMethod.GET, "/api/me", stale))) {
      assertThat(response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, java.util.List.of()))
          .noneMatch(c -> c.startsWith("pu_session=") && !c.contains("Max-Age=0"));
    }
  }

  @Test
  void spoofedUserHeadersDoNotChangeTheSessionUser() {
    String email = email();
    start(email);
    var verify =
        rest.postForEntity(
            "/api/auth/email/verify", Map.of("token", mail.tokenTo(email)), ProfileDto.class);
    HttpHeaders headers = withCookie(sessionCookie(verify));
    headers.add("X-User-Id", UUID.randomUUID().toString());

    var me =
        rest.exchange(
            "/api/me?userId=" + UUID.randomUUID(),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            ProfileDto.class);

    assertThat(me.getBody().id()).isEqualTo(verify.getBody().id());
  }

  @Test
  void signOutDeletesTheSessionAndTheOldCookieIs401() {
    String email = email();
    start(email);
    var verify =
        rest.postForEntity(
            "/api/auth/email/verify", Map.of("token", mail.tokenTo(email)), String.class);
    String cookie = sessionCookie(verify);
    String sessionId = sessionIdOf(cookie);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id = ?",
                Integer.class,
                sessionId))
        .isEqualTo(1);

    var out = exchange(HttpMethod.POST, "/api/auth/sign-out", withCookie(cookie));

    assertThat(out.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(lastSessionCookie(out)).contains("Max-Age=0");
    assertThat(exchange(HttpMethod.GET, "/api/me", withCookie(cookie)).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id = ?",
                Integer.class,
                sessionId))
        .isZero();
  }
}
