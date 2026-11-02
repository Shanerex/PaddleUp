package com.paddleup.accounts.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.paddleup.accounts.dto.ProfileDto;
import com.paddleup.support.AbstractPostgresIT;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class GoogleLoginIT extends AbstractPostgresIT {

  static final String WEB = "http://web.test";
  static final String CLIENT_ID = "test-client";
  static final String ACCESS_TOKEN = "access-token-that-must-not-be-stored";

  static RSAKey signingKey;

  @RegisterExtension
  static WireMockExtension google =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void googleProps(DynamicPropertyRegistry registry) throws Exception {
    signingKey = new RSAKeyGenerator(2048).keyID("k1").generate();
    String base = "spring.security.oauth2.client.provider.google.";
    registry.add(base + "authorization-uri", () -> google.baseUrl() + "/auth");
    registry.add(base + "token-uri", () -> google.baseUrl() + "/token");
    registry.add(base + "jwk-set-uri", () -> google.baseUrl() + "/jwks");
    String reg = "spring.security.oauth2.client.registration.google.";
    registry.add(reg + "client-id", () -> CLIENT_ID);
    registry.add(reg + "client-secret", () -> "test-secret");
    registry.add("paddleup.auth.web-base-url", () -> WEB);
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

  final HttpClient http =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  @BeforeEach
  void stubJwks() {
    google.resetAll();
    google.stubFor(
        get(urlEqualTo("/jwks"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(new JWKSet(signingKey.toPublicJWK()).toString())));
  }

  private HttpResponse<String> send(String method, String path, String cookie) throws Exception {
    var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (cookie != null) b.header("Cookie", cookie);
    b.method(method, HttpRequest.BodyPublishers.noBody());
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String cookieOf(HttpResponse<?> response) {
    return response.headers().allValues("Set-Cookie").stream()
        .filter(c -> c.startsWith("pu_session="))
        .map(c -> c.substring(0, c.indexOf(';')))
        .reduce((first, second) -> second)
        .orElse(null);
  }

  private static Map<String, String> query(String location) {
    Map<String, String> params = new HashMap<>();
    for (String pair : URI.create(location).getRawQuery().split("&")) {
      String[] kv = pair.split("=", 2);
      params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
    }
    return params;
  }

  private String idToken(String nonce, String sub, String email, boolean verified)
      throws Exception {
    Instant now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer("https://accounts.google.com")
            .subject(sub)
            .audience(CLIENT_ID)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("nonce", nonce)
            .claim("email", email)
            .claim("email_verified", verified)
            .claim("name", "Gina <b>Google</b>")
            .claim("picture", "https://example.test/gina.png")
            .build();
    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
    jwt.sign(new RSASSASigner(signingKey));
    return jwt.serialize();
  }

  /** Runs the full authorization-code flow and returns the callback response. */
  private HttpResponse<String> googleCallback(String sub, String email, boolean verified)
      throws Exception {
    var start = send("GET", "/api/oauth2/authorization/google", null);
    assertThat(start.statusCode()).isEqualTo(302);
    String location = start.headers().firstValue("Location").orElseThrow();
    assertThat(location).startsWith(google.baseUrl() + "/auth");
    var params = query(location);
    assertThat(params.get("redirect_uri")).isEqualTo(WEB + "/api/login/oauth2/code/google");
    assertThat(params.get("scope").split(" "))
        .containsExactlyInAnyOrder("openid", "email", "profile");

    google.stubFor(
        post(urlEqualTo("/token"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"access_token\":\""
                            + ACCESS_TOKEN
                            + "\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"openid email profile\",\"id_token\":\""
                            + idToken(params.get("nonce"), sub, email, verified)
                            + "\"}")));
    return send(
        "GET",
        "/api/login/oauth2/code/google?code=abc&state=" + params.get("state"),
        cookieOf(start));
  }

  private HttpResponse<String> me(String cookie) throws Exception {
    return send("GET", "/api/me", cookie);
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  @Test
  void newGoogleUserIsCreatedAndSignedIn() throws Exception {
    String sub = UUID.randomUUID().toString();
    String email = "gl-" + UUID.randomUUID() + "@example.test";

    var callback = googleCallback(sub, email, true);

    assertThat(callback.statusCode()).isEqualTo(302);
    assertThat(callback.headers().firstValue("Location")).contains(WEB + "/profile");
    String cookie = cookieOf(callback);
    var me = me(cookie);
    assertThat(me.statusCode()).isEqualTo(200);
    ProfileDto profile = json.readValue(me.body(), ProfileDto.class);
    assertThat(profile.email()).isEqualTo(email);
    assertThat(profile.displayName()).doesNotContain("<");
    assertThat(profile.googlePictureUrl()).isEqualTo("https://example.test/gina.png");
    assertThat(count("SELECT count(*) FROM users WHERE email = ?", email)).isEqualTo(1);
  }

  @Test
  void sessionHoldsOnlyTheUserIdNotGoogleTokens() throws Exception {
    var callback =
        googleCallback(
            UUID.randomUUID().toString(), "tok-" + UUID.randomUUID() + "@example.test", true);
    String cookie = cookieOf(callback);
    String sessionId =
        new String(
            Base64.getDecoder().decode(cookie.substring("pu_session=".length())),
            StandardCharsets.UTF_8);

    List<String> names =
        jdbc.queryForList(
            "SELECT a.attribute_name FROM spring_session_attributes a"
                + " JOIN spring_session s ON s.primary_id = a.session_primary_id"
                + " WHERE s.session_id = ?",
            String.class,
            sessionId);
    assertThat(names).containsExactly("SPRING_SECURITY_CONTEXT");
    byte[] bytes =
        jdbc.queryForObject(
            "SELECT a.attribute_bytes FROM spring_session_attributes a"
                + " JOIN spring_session s ON s.primary_id = a.session_primary_id"
                + " WHERE s.session_id = ?",
            byte[].class,
            sessionId);
    assertThat(new String(bytes, StandardCharsets.ISO_8859_1))
        .doesNotContain(ACCESS_TOKEN)
        .doesNotContain("eyJ")
        .contains("AccountPrincipal");
  }

  @Test
  void repeatCallbackSignsIntoTheSameAccount() throws Exception {
    String sub = UUID.randomUUID().toString();
    String email = "rep-" + UUID.randomUUID() + "@example.test";

    var first = googleCallback(sub, email, true);
    var second = googleCallback(sub, email, true);

    ProfileDto a = json.readValue(me(cookieOf(first)).body(), ProfileDto.class);
    ProfileDto b = json.readValue(me(cookieOf(second)).body(), ProfileDto.class);
    assertThat(b.id()).isEqualTo(a.id());
    assertThat(count("SELECT count(*) FROM users WHERE email = ?", email)).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM user_identities WHERE subject = ?", sub)).isEqualTo(1);
  }

  @Test
  void unverifiedEmailIsRejectedWithNoSessionAndNoAccount() throws Exception {
    String sub = UUID.randomUUID().toString();
    String email = "unv-" + UUID.randomUUID() + "@example.test";

    var callback = googleCallback(sub, email, false);

    assertThat(callback.statusCode()).isEqualTo(302);
    assertThat(callback.headers().firstValue("Location").orElseThrow())
        .startsWith(WEB + "/sign-in?error=unverified_email");
    String cookie = cookieOf(callback);
    if (cookie != null) {
      assertThat(me(cookie).statusCode()).isEqualTo(401);
    }
    assertThat(count("SELECT count(*) FROM users WHERE email = ?", email)).isZero();
    assertThat(count("SELECT count(*) FROM user_identities WHERE subject = ?", sub)).isZero();
  }

  @Test
  void callbackWithUnknownStateRedirectsToSignInError() throws Exception {
    var callback = send("GET", "/api/login/oauth2/code/google?code=abc&state=forged", null);

    assertThat(callback.statusCode()).isEqualTo(302);
    assertThat(callback.headers().firstValue("Location").orElseThrow())
        .startsWith(WEB + "/sign-in?error=google");
  }
}
