package com.paddleup.accounts.service;

import com.paddleup.accounts.dto.ProfileDto;
import com.paddleup.accounts.dto.UpdateProfileRequest;
import com.paddleup.accounts.dto.UserId;
import com.paddleup.accounts.exception.InvalidRequestException;
import com.paddleup.accounts.exception.NotSignedInException;
import com.paddleup.accounts.model.User;
import com.paddleup.accounts.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

  private final UserRepository users;
  private final AvatarPolicy avatars;
  private final Clock clock;

  public ProfileService(UserRepository users, AvatarPolicy avatars, Clock clock) {
    this.users = users;
    this.avatars = avatars;
    this.clock = clock;
  }

  /** The caller's profile; a session whose account no longer exists counts as not signed in. */
  public ProfileDto get(UserId id) {
    return toDto(users.findById(id.value()).orElseThrow(NotSignedInException::new));
  }

  /** Applies the supplied fields, validating each before any is written. */
  @Transactional
  public ProfileDto update(UserId id, UpdateProfileRequest request) {
    if (request == null || (request.displayName() == null && request.avatar() == null)) {
      throw new InvalidRequestException("Nothing to update");
    }
    User user = users.findById(id.value()).orElseThrow(NotSignedInException::new);
    String name =
        request.displayName() == null
            ? null
            : DisplayNameSanitiser.requireValid(request.displayName());
    if (request.avatar() != null && !avatars.isAllowed(user, request.avatar())) {
      throw new InvalidRequestException("Avatar must be a preset or your Google picture");
    }
    Instant now = clock.instant();
    if (name != null) {
      users.updateDisplayName(user.id(), name, now);
    }
    if (request.avatar() != null) {
      users.updateAvatar(user.id(), request.avatar(), now);
    }
    return get(id);
  }

  public static ProfileDto toDto(User user) {
    return new ProfileDto(
        user.id(), user.email(), user.displayName(), user.avatar(), user.googlePictureUrl());
  }
}
