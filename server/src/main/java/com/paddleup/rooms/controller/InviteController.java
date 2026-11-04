package com.paddleup.rooms.controller;

import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.rooms.dto.InviteDto;
import com.paddleup.rooms.service.InviteService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{id}/invite")
class InviteController {

  private final CurrentUser currentUser;
  private final InviteService invites;

  InviteController(CurrentUser currentUser, InviteService invites) {
    this.currentUser = currentUser;
    this.invites = invites;
  }

  @GetMapping
  InviteDto current(@PathVariable UUID id) {
    return invites.current(currentUser.require(), id);
  }

  /** Issues the first code, or regenerates: the new code replaces the old one. */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  InviteDto issue(@PathVariable UUID id) {
    return invites.issue(currentUser.require(), id);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revoke(@PathVariable UUID id) {
    invites.revoke(currentUser.require(), id);
  }
}
