package com.paddleup.rooms.controller;

import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.rooms.dto.HandoverDto;
import com.paddleup.rooms.dto.HandoverRequest;
import com.paddleup.rooms.service.CommissionerService;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{id}/commissioner")
class CommissionerController {

  private final CurrentUser currentUser;
  private final CommissionerService commissioners;

  CommissionerController(CurrentUser currentUser, CommissionerService commissioners) {
    this.currentUser = currentUser;
    this.commissioners = commissioners;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  HandoverDto handover(@PathVariable UUID id, @RequestBody HandoverRequest request) {
    return commissioners.handover(currentUser.require(), id, request);
  }
}
