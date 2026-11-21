package com.paddleup.scoring.controller;

import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.scoring.dto.OverrideLogEntryDto;
import com.paddleup.scoring.service.ManualStatsService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The room's override log, oldest first, paged by {@code seq}; any member reads. */
@RestController
@RequestMapping("/api/rooms/{roomId}/scoring/override-log")
class OverrideLogController {

  private final CurrentUser currentUser;
  private final ManualStatsService manual;

  OverrideLogController(CurrentUser currentUser, ManualStatsService manual) {
    this.currentUser = currentUser;
    this.manual = manual;
  }

  @GetMapping
  List<OverrideLogEntryDto> overrideLog(
      @PathVariable UUID roomId,
      @RequestParam(defaultValue = "0") long after,
      @RequestParam(defaultValue = "50") int limit) {
    return manual.overrideLog(roomId, currentUser.require(), after, limit);
  }
}
