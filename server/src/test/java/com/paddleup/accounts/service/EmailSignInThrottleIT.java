package com.paddleup.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.accounts.exception.InvalidLoginException;
import com.paddleup.accounts.exception.InvalidRequestException;
import com.paddleup.accounts.exception.TooManyRequestsException;
import com.paddleup.support.AbstractPostgresIT;
import com.paddleup.support.CapturingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** Rate limits on email sign-in: per email and per address, over a sliding window (S-1, S-2). */
@Import(EmailSignInThrottleIT.Capture.class)
@TestPropertySource(
    properties = {
      "paddleup.auth.throttle.start-per-email=3",
      "paddleup.auth.throttle.start-per-ip=4",
      "paddleup.auth.throttle.start-window=15m",
      "paddleup.auth.throttle.failed-codes-per-email=5",
      "paddleup.auth.throttle.failed-per-ip=8",
      "paddleup.auth.throttle.failure-window=1h"
    })
class EmailSignInThrottleIT extends AbstractPostgresIT {

  @TestConfiguration
  static class Capture {
    @Bean
    @Primary
    CapturingEmailSender capturingEmailSender() {
      return new CapturingEmailSender();
    }
  }

  @Autowired EmailSignInService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired CapturingEmailSender mail;

  private static String email() {
    return "throttle-" + UUID.randomUUID() + "@example.test";
  }

  private static String ip() {
    return "198.51.100." + (1 + (int) (Math.random() * 250)) + "/" + UUID.randomUUID();
  }

  private int tokensFor(String email) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM login_tokens WHERE email = ?", Integer.class, email.toLowerCase());
  }

  private void ageThrottleEvents(String interval) {
    jdbc.update(
        "UPDATE auth_throttle_events SET occurred_at = occurred_at - ?::interval", interval);
  }

  private static String wrongFor(String code) {
    return code.equals("000000") ? "111111" : "000000";
  }

  @Test
  void startIsLimitedPerEmailAndMintsNothingPastTheLimit() {
    String email = email();
    for (int i = 0; i < 3; i++) {
      service.start(email, ip());
    }

    assertThatThrownBy(() -> service.start(email, ip()))
        .isInstanceOf(TooManyRequestsException.class)
        .satisfies(
            e ->
                assertThat(((TooManyRequestsException) e).retryAfter().toSeconds())
                    .isBetween(1L, 15L * 60));
    assertThat(tokensFor(email)).isEqualTo(3);
  }

  @Test
  void emailLimitIgnoresCaseAndSurroundingSpace() {
    String email = email();
    service.start(email, ip());
    service.start(email.toUpperCase(), ip());
    service.start("  " + email + " ", ip());

    assertThatThrownBy(() -> service.start(email, ip()))
        .isInstanceOf(TooManyRequestsException.class);
  }

  @Test
  void startIsLimitedPerAddressAcrossEmails() {
    String ip = ip();
    for (int i = 0; i < 4; i++) {
      service.start(email(), ip);
    }
    String blocked = email();

    assertThatThrownBy(() -> service.start(blocked, ip))
        .isInstanceOf(TooManyRequestsException.class);
    assertThat(tokensFor(blocked)).isZero();
    service.start(blocked, ip()); // another address is unaffected
  }

  @Test
  void invalidEmailDoesNotConsumeTheBudget() {
    String ip = ip();
    for (int i = 0; i < 10; i++) {
      assertThatThrownBy(() -> service.start("nope", ip))
          .isInstanceOf(InvalidRequestException.class);
    }
    service.start(email(), ip);
  }

  @Test
  void startWindowSlides() {
    String email = email();
    for (int i = 0; i < 3; i++) {
      service.start(email, ip());
    }
    ageThrottleEvents("16 minutes");

    service.start(email, ip());

    assertThat(tokensFor(email)).isEqualTo(4);
  }

  @Test
  void mintingNewTokensDoesNotResetTheFailedCodeBudget() {
    String email = email();
    String ip = ip();
    var first = service.issue(email);
    for (int i = 0; i < 3; i++) {
      assertThatThrownBy(() -> service.redeemCode(email, wrongFor(first.code()), ip))
          .isInstanceOf(InvalidLoginException.class);
    }
    var second = service.issue(email);
    for (int i = 0; i < 2; i++) {
      assertThatThrownBy(() -> service.redeemCode(email, wrongFor(second.code()), ip))
          .isInstanceOf(InvalidLoginException.class);
    }
    var third = service.issue(email);

    // Five misses in the window lock the email, even for a fresh token and the right code.
    assertThatThrownBy(() -> service.redeemCode(email, third.code(), ip))
        .isInstanceOf(TooManyRequestsException.class)
        .satisfies(
            e ->
                assertThat(((TooManyRequestsException) e).retryAfter().toSeconds())
                    .isBetween(1L, 60L * 60));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM login_tokens WHERE email = ? AND used_at IS NOT NULL",
                Integer.class,
                email))
        .isZero();

    ageThrottleEvents("61 minutes");
    assertThat(service.redeemCode(email, third.code(), ip).email()).isEqualTo(email);
  }

  @Test
  void lockedEmailDoesNotExtendItsOwnLock() {
    String email = email();
    var issued = service.issue(email);
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> service.redeemCode(email, wrongFor(issued.code()), ip()))
          .isInstanceOf(InvalidLoginException.class);
    }
    int locked = failEvents();
    for (int i = 0; i < 7; i++) {
      assertThatThrownBy(() -> service.redeemCode(email, wrongFor(issued.code()), ip()))
          .isInstanceOf(TooManyRequestsException.class);
    }

    assertThat(failEvents()).isEqualTo(locked);
    ageThrottleEvents("61 minutes");
    assertThat(service.redeemCode(email, service.issue(email).code(), ip()).email())
        .isEqualTo(email);
  }

  private int failEvents() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM auth_throttle_events WHERE scope = 'code-fail-email'", Integer.class);
  }

  @Test
  void failedGuessesAreLimitedPerAddressAcrossEmails() {
    String ip = ip();
    for (int i = 0; i < 8; i++) {
      String email = email();
      var issued = service.issue(email);
      assertThatThrownBy(() -> service.redeemCode(email, wrongFor(issued.code()), ip))
          .isInstanceOf(InvalidLoginException.class);
    }
    String victim = email();
    var issued = service.issue(victim);

    assertThatThrownBy(() -> service.redeemCode(victim, issued.code(), ip))
        .isInstanceOf(TooManyRequestsException.class);
    assertThatThrownBy(() -> service.redeemLink(issued.token(), ip))
        .isInstanceOf(TooManyRequestsException.class);
    assertThat(service.redeemCode(victim, issued.code(), ip()).email()).isEqualTo(victim);
  }

  @Test
  void badLinksCountAgainstTheAddress() {
    String ip = ip();
    for (int i = 0; i < 8; i++) {
      assertThatThrownBy(() -> service.redeemLink("bad-" + UUID.randomUUID(), ip))
          .isInstanceOf(InvalidLoginException.class);
    }

    assertThatThrownBy(() -> service.redeemLink("bad", ip))
        .isInstanceOf(TooManyRequestsException.class);
  }

  @Test
  void successfulSignInsAreNotCountedAsFailures() {
    String ip = ip();
    for (int i = 0; i < 12; i++) {
      String email = email();
      assertThat(service.redeemCode(email, service.issue(email).code(), ip)).isNotNull();
    }
  }

  @Test
  void startedTokenStillSignsInWithinTheLimits() {
    String email = email();
    service.start(email, ip());

    assertThat(service.redeemCode(email, mail.codeTo(email), ip()).email()).isEqualTo(email);
  }

  @Test
  void throttleRowsStoreNoRawAddresses() {
    String email = email();
    service.start(email, "203.0.113.77");

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auth_throttle_events WHERE key_hash LIKE '%203.0.113.77%'"
                    + " OR key_hash LIKE '%example.test%'",
                Integer.class))
        .isZero();
  }
}
