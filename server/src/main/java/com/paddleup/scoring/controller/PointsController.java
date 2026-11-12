package com.paddleup.scoring.controller;

import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.scoring.dto.MatchPointsDto;
import com.paddleup.scoring.service.ScoringService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A room's stored points per match, with the rule lines behind each total; any member reads. */
@RestController
@RequestMapping("/api/rooms/{roomId}/scoring/matches")
class PointsController {

  private final CurrentUser currentUser;
  private final ScoringService scoring;

  PointsController(CurrentUser currentUser, ScoringService scoring) {
    this.currentUser = currentUser;
    this.scoring = scoring;
  }

  @GetMapping("/{matchId}/points")
  MatchPointsDto matchPoints(@PathVariable UUID roomId, @PathVariable String matchId) {
    return scoring.matchPoints(roomId, currentUser.require(), matchId);
  }
}
