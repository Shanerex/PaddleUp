package com.paddleup.rooms.controller;

import com.paddleup.accounts.service.ClientAddress;
import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.rooms.dto.JoinPreviewDto;
import com.paddleup.rooms.dto.JoinPreviewRequest;
import com.paddleup.rooms.dto.JoinRequest;
import com.paddleup.rooms.dto.JoinResultDto;
import com.paddleup.rooms.service.JoinService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/join")
class JoinController {

  private final CurrentUser currentUser;
  private final ClientAddress clientAddress;
  private final JoinService joins;

  JoinController(CurrentUser currentUser, ClientAddress clientAddress, JoinService joins) {
    this.currentUser = currentUser;
    this.clientAddress = clientAddress;
    this.joins = joins;
  }

  /** Joins with a code; repeating it returns the franchise the caller already has. */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  JoinResultDto join(@RequestBody JoinRequest request, HttpServletRequest http) {
    return joins.redeem(currentUser.require(), request, clientAddress.limitKey(http));
  }

  @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
  JoinPreviewDto preview(@RequestBody JoinPreviewRequest request, HttpServletRequest http) {
    return joins.preview(
        currentUser.require(),
        request == null ? null : request.code(),
        clientAddress.limitKey(http));
  }
}
