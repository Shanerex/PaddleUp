package com.paddleup.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.accounts.exception.UnverifiedEmailException;
import com.paddleup.accounts.model.User;
import com.paddleup.support.AbstractPostgresIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class GoogleSignInServiceIT extends AbstractPostgresIT {

  @Autowired GoogleSignInService google;
  @Autowired EmailSignInService emailSignIn;
  @Autowired JdbcTemplate jdbc;

  private static String id() {
    return UUID.randomUUID().toString();
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  @Test
  void newIdentityCreatesExactlyOneAccountAndRepeatSignsIntoIt() {
    String sub = id();
    String email = "g-" + id() + "@example.test";
    var profile =
        new GoogleProfile(sub, email, true, "Gina Google", "https://example.test/gina.png");

    User first = google.signIn(profile);
    User again = google.signIn(profile);

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(first.email()).isEqualTo(email);
    assertThat(first.displayName()).isEqualTo("Gina Google");
    assertThat(first.googlePictureUrl()).isEqualTo("https://example.test/gina.png");
    assertThat(count("SELECT count(*) FROM users WHERE email = ?", email)).isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM user_identities WHERE provider = 'google' AND subject = ?",
                sub))
        .isEqualTo(1);
  }

  @Test
  void verifiedEmailLinksToTheExistingEmailAccount() {
    String email = "link-" + id() + "@example.test";
    User existing = emailSignIn.redeemCode(email, emailSignIn.issue(email).code(), "203.0.113.9");
    String sub = id();

    User signedIn =
        google.signIn(
            new GoogleProfile(
                sub, email.toUpperCase(), true, "Other Name", "https://example.test/o.png"));

    assertThat(signedIn.id()).isEqualTo(existing.id());
    assertThat(signedIn.displayName()).isEqualTo(existing.displayName());
    assertThat(
            count(
                "SELECT count(*) FROM user_identities WHERE subject = ? AND user_id = ?",
                sub,
                existing.id()))
        .isEqualTo(1);
    assertThat(google.signIn(new GoogleProfile(sub, email, true, "x", null)).id())
        .isEqualTo(existing.id());
  }

  @Test
  void unverifiedEmailIsRejectedAndNeverLinks() {
    String email = "unv-" + id() + "@example.test";
    User existing = emailSignIn.redeemCode(email, emailSignIn.issue(email).code(), "203.0.113.9");
    String sub = id();

    assertThatThrownBy(() -> google.signIn(new GoogleProfile(sub, email, false, "Mallory", null)))
        .isInstanceOf(UnverifiedEmailException.class);

    assertThat(count("SELECT count(*) FROM user_identities WHERE subject = ?", sub)).isZero();
    assertThat(count("SELECT count(*) FROM users WHERE email = ?", email)).isEqualTo(1);
  }

  @Test
  void unverifiedNewEmailCreatesNothing() {
    String email = "unv-new-" + id() + "@example.test";

    assertThatThrownBy(() -> google.signIn(new GoogleProfile(id(), email, false, "Nobody", null)))
        .isInstanceOf(UnverifiedEmailException.class);

    assertThat(count("SELECT count(*) FROM users WHERE email = ?", email)).isZero();
  }

  @Test
  void markupInGoogleNameIsNotStoredVerbatim() {
    User user =
        google.signIn(
            new GoogleProfile(
                id(), "mk-" + id() + "@example.test", true, "<b>Bold</b><script>x</script>", null));

    assertThat(user.displayName()).doesNotContain("<").doesNotContain(">");
    assertThat(user.displayName()).hasSizeBetween(2, 30);
  }

  @Test
  void onlyHttpsPictureUrlsAreStored() {
    User user =
        google.signIn(
            new GoogleProfile(
                id(), "pic-" + id() + "@example.test", true, "Pic Person", "javascript:alert(1)"));

    assertThat(user.googlePictureUrl()).isNull();
  }
}
