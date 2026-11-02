package com.paddleup.accounts.service;

import com.paddleup.accounts.config.AuthProperties;
import com.paddleup.accounts.dto.AvatarPresetsDto;
import com.paddleup.accounts.model.User;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/** Allowed avatars: a configured preset id, or exactly the user's stored Google picture URL. */
@Component
public class AvatarPolicy {

  private final List<String> presets;

  public AvatarPolicy(AuthProperties props) {
    this.presets =
        IntStream.rangeClosed(1, props.avatarPresetCount())
            .mapToObj(i -> String.format("preset-%02d", i))
            .toList();
  }

  public AvatarPresetsDto presets() {
    return new AvatarPresetsDto(presets);
  }

  public boolean isAllowed(User user, String avatar) {
    if (avatar == null) {
      return false;
    }
    return presets.contains(avatar)
        || (user.googlePictureUrl() != null && user.googlePictureUrl().equals(avatar));
  }
}
