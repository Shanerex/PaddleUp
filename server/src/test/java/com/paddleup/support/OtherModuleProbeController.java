package com.paddleup.support;

import com.paddleup.accounts.service.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stands in for another module's controller, which asks CurrentUser who is calling. */
@RestController
class OtherModuleProbeController {

  private final CurrentUser currentUser;

  OtherModuleProbeController(CurrentUser currentUser) {
    this.currentUser = currentUser;
  }

  @GetMapping("/api/probe/whoami")
  String whoami() {
    return currentUser.require().toString();
  }
}
