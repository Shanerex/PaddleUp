package com.paddleup.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.accounts.exception.InvalidLoginException;
import com.paddleup.accounts.exception.TooManyRequestsException;
import com.paddleup.accounts.model.User;
import com.paddleup.support.AbstractPostgresIT;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class EmailSignInServiceIT extends AbstractPostgresIT {

  @Autowired EmailSignInService service;
  @Autowired JdbcTemplate jdbc;

  private static String email() {
    return "Person-" + UUID.randomUUID() + "@Example.test";
  }

  private int sessionRows() {
    return jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class);
  }

  @Test
  void codeSignsInAndCreatesAccountOnce() {
    String email = email();
    var issued = service.issue(email);
    User first = redeemCode(email, issued.code());

    assertThat(first.email()).isEqualTo(email.toLowerCase());
    assertThat(first.displayName()).hasSizeBetween(2, 30);
    assertThat(first.avatar()).isEqualTo("preset-01");

    User second = redeemCode(email, service.issue(email).code());
    assertThat(second.id()).isEqualTo(first.id());
  }

  @Test
  void linkSignsInThenCannotBeReused() {
    String email = email();
    var issued = service.issue(email);

    User user = redeemLink(issued.token());

    assertThat(user.email()).isEqualTo(email.toLowerCase());
    assertThatThrownBy(() -> redeemLink(issued.token())).isInstanceOf(InvalidLoginException.class);
  }

  @Test
  void codeCannotBeReusedAndLinkOfSameTokenIsAlsoSpent() {
    String email = email();
    var issued = service.issue(email);
    redeemCode(email, issued.code());

    assertThatThrownBy(() -> redeemCode(email, issued.code()))
        .isInstanceOf(InvalidLoginException.class);
    assertThatThrownBy(() -> redeemLink(issued.token())).isInstanceOf(InvalidLoginException.class);
  }

  @Test
  void wrongLinkAndWrongCodeAreRejected() {
    String email = email();
    var issued = service.issue(email);
    String wrong = issued.code().equals("000000") ? "111111" : "000000";

    assertThatThrownBy(() -> redeemLink("not-a-real-token"))
        .isInstanceOf(InvalidLoginException.class);
    assertThatThrownBy(() -> redeemCode(email, wrong)).isInstanceOf(InvalidLoginException.class);
    assertThatThrownBy(() -> redeemCode(email(), issued.code()))
        .isInstanceOf(InvalidLoginException.class);
    assertThatThrownBy(() -> redeemCode(email, "abc")).isInstanceOf(InvalidLoginException.class);
  }

  @Test
  void expiredLinkAndCodeAreRejected() {
    String email = email();
    var issued = service.issue(email);
    jdbc.update(
        "UPDATE login_tokens SET expires_at = now() - interval '1 second' WHERE email = ?",
        email.toLowerCase());

    assertThatThrownBy(() -> redeemLink(issued.token())).isInstanceOf(InvalidLoginException.class);
    assertThatThrownBy(() -> redeemCode(email, issued.code()))
        .isInstanceOf(InvalidLoginException.class);
  }

  @Test
  void tokenLivesFifteenMinutes() {
    String email = email();
    service.issue(email);

    Long seconds =
        jdbc.queryForObject(
            "SELECT extract(epoch FROM (expires_at - created_at))::bigint FROM login_tokens"
                + " WHERE email = ?",
            Long.class,
            email.toLowerCase());
    assertThat(seconds).isEqualTo(15 * 60);
  }

  @Test
  void secretsAreStoredHashedOnly() {
    String email = email();
    var issued = service.issue(email);

    var row =
        jdbc.queryForMap(
            "SELECT link_hash, code_hash FROM login_tokens WHERE email = ?", email.toLowerCase());
    assertThat(row.values()).doesNotContain(issued.token(), issued.code());
  }

  @Test
  void codeIsLockedAfterFiveWrongAttempts() {
    String email = email();
    var issued = service.issue(email);
    String wrong = issued.code().equals("000000") ? "111111" : "000000";

    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> redeemCode(email, wrong)).isInstanceOf(InvalidLoginException.class);
    }

    assertThatThrownBy(() -> redeemCode(email, issued.code()))
        .isInstanceOf(TooManyRequestsException.class);
  }

  @Test
  void firstSignInsRacingForOneEmailBothSucceedWithOneAccount() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 15; round++) {
        String email = email();
        String a = service.issue(email).token();
        String b = service.issue(email).token();
        var barrier = new CyclicBarrier(2);
        Callable<User> first = () -> raced(barrier, a);
        Callable<User> second = () -> raced(barrier, b);

        var results = pool.invokeAll(List.of(first, second));

        assertThat(results.get(0).get().id()).isEqualTo(results.get(1).get().id());
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM users WHERE email = ?",
                    Integer.class,
                    email.toLowerCase()))
            .isEqualTo(1);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private User raced(CyclicBarrier barrier, String token) throws Exception {
    barrier.await();
    return redeemLink(token);
  }

  @Test
  void failedRedemptionsCreateNoSession() {
    int before = sessionRows();
    String email = email();
    var issued = service.issue(email);

    assertThatThrownBy(() -> redeemLink("nope")).isInstanceOf(InvalidLoginException.class);
    String wrong = issued.code().equals("999999") ? "888888" : "999999";
    assertThatThrownBy(() -> redeemCode(email, wrong)).isInstanceOf(InvalidLoginException.class);
    redeemCode(email, issued.code());

    assertThat(sessionRows()).isEqualTo(before);
  }

  private User redeemCode(String email, String code) {
    return service.redeemCode(email, code, "203.0.113.9");
  }

  private User redeemLink(String token) {
    return service.redeemLink(token, "203.0.113.9");
  }
}
