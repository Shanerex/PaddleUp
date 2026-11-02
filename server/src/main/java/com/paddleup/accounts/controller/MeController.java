package com.paddleup.accounts.controller;

import com.paddleup.accounts.dto.AvatarPresetsDto;
import com.paddleup.accounts.dto.ProfileDto;
import com.paddleup.accounts.dto.UpdateProfileRequest;
import com.paddleup.accounts.service.AvatarPolicy;
import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.accounts.service.ProfileService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class MeController {

  private final CurrentUser currentUser;
  private final ProfileService profiles;
  private final AvatarPolicy avatars;

  MeController(CurrentUser currentUser, ProfileService profiles, AvatarPolicy avatars) {
    this.avatars = avatars;
    this.currentUser = currentUser;
    this.profiles = profiles;
  }

  @GetMapping("/me")
  ProfileDto me() {
    return profiles.get(currentUser.require());
  }

  @PatchMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
  ProfileDto update(@RequestBody UpdateProfileRequest request) {
    return profiles.update(currentUser.require(), request);
  }

  @GetMapping("/avatars")
  AvatarPresetsDto avatars() {
    return avatars.presets();
  }
}
