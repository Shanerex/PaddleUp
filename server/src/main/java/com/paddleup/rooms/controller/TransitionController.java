package com.paddleup.rooms.controller;

import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.rooms.dto.TransitionDto;
import com.paddleup.rooms.dto.TransitionRequest;
import com.paddleup.rooms.service.RoomLifecycle;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{id}/transitions")
class TransitionController {

  private final CurrentUser currentUser;
  private final RoomLifecycle lifecycle;

  TransitionController(CurrentUser currentUser, RoomLifecycle lifecycle) {
    this.currentUser = currentUser;
    this.lifecycle = lifecycle;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  TransitionDto advance(@PathVariable UUID id, @RequestBody TransitionRequest request) {
    return lifecycle.advanceAsCommissioner(
        currentUser.require(), id, request == null ? null : request.to());
  }

  @GetMapping
  List<TransitionDto> history(@PathVariable UUID id) {
    return lifecycle.history(currentUser.require(), id);
  }
}
