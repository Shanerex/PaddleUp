package com.paddleup.accounts.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.accounts.model.User;
import com.paddleup.accounts.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev-only: creates test accounts directly, skipping the email sign-in flow. Used by the dev seed
 * only; the dev profile is the only place it exists.
 */
@Service
@Profile("dev")
public class DevAccountSeeder {

  private final UserRepository users;
  private final Clock clock;

  public DevAccountSeeder(UserRepository users, Clock clock) {
    this.users = users;
    this.clock = clock;
  }

  /** The account for this email, created with the given name when missing. Idempotent. */
  @Transactional
  public UserId ensure(String email, String displayName) {
    String address = EmailSignInService.normalise(email);
    return users
        .findByEmail(address)
        .map(u -> new UserId(u.id()))
        .orElseGet(
            () -> {
              Instant now = clock.instant();
              User created =
                  users.insert(
                      new User(
                          UUID.randomUUID(),
                          address,
                          displayName,
                          EmailSignInService.DEFAULT_AVATAR,
                          null,
                          now,
                          now));
              return new UserId(created.id());
            });
  }
}
