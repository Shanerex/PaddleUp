package com.paddleup.accounts.service;

import com.paddleup.accounts.exception.UnverifiedEmailException;
import com.paddleup.accounts.model.Identity;
import com.paddleup.accounts.model.User;
import com.paddleup.accounts.repository.IdentityRepository;
import com.paddleup.accounts.repository.UserRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Resolves a Google profile to an account: by (google, sub), else by verified email, else create.
 */
@Service
public class GoogleSignInService {

  static final String PROVIDER = "google";
  private static final int MAX_URL = 2048;

  private final UserRepository users;
  private final IdentityRepository identities;
  private final Clock clock;
  private final TransactionTemplate tx;

  public GoogleSignInService(
      UserRepository users, IdentityRepository identities, Clock clock, TransactionTemplate tx) {
    this.users = users;
    this.identities = identities;
    this.clock = clock;
    this.tx = tx;
  }

  public User signIn(GoogleProfile profile) {
    try {
      return tx.execute(status -> resolve(profile));
    } catch (DuplicateKeyException race) {
      // A concurrent first sign-in won; the second attempt finds its rows.
      return tx.execute(status -> resolve(profile));
    }
  }

  private User resolve(GoogleProfile profile) {
    String picture = safePicture(profile.pictureUrl());
    Optional<UUID> linked = identities.findUserId(PROVIDER, profile.subject());
    if (linked.isPresent()) {
      return refreshPicture(users.findById(linked.get()).orElseThrow(), picture);
    }
    if (!profile.emailVerified() || profile.email() == null || profile.email().isBlank()) {
      throw new UnverifiedEmailException();
    }
    String email = EmailSignInService.normalise(profile.email());
    Instant now = clock.instant();
    Optional<User> byEmail = users.findByEmail(email);
    User user;
    if (byEmail.isPresent()) {
      user = refreshPicture(byEmail.get(), picture);
    } else {
      user =
          users.insert(
              new User(
                  UUID.randomUUID(),
                  email,
                  EmailSignInService.defaultName(profile.name(), email),
                  EmailSignInService.DEFAULT_AVATAR,
                  picture,
                  now,
                  now));
    }
    identities.insert(new Identity(user.id(), PROVIDER, profile.subject(), now));
    return user;
  }

  /** Keeps the stored Google picture current; moves the avatar with it if it was in use. */
  private User refreshPicture(User user, String picture) {
    if (picture == null || Objects.equals(picture, user.googlePictureUrl())) {
      return user;
    }
    Instant now = clock.instant();
    users.updateGooglePictureUrl(user.id(), picture, now);
    if (user.googlePictureUrl() != null && user.googlePictureUrl().equals(user.avatar())) {
      users.updateAvatar(user.id(), picture, now);
    }
    return users.findById(user.id()).orElseThrow();
  }

  private static String safePicture(String url) {
    if (url == null || url.length() > MAX_URL) {
      return null;
    }
    try {
      URI uri = URI.create(url);
      return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? url : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
