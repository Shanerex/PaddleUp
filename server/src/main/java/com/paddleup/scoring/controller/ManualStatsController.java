package com.paddleup.scoring.controller;

import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.scoring.dto.ClearFieldsRequest;
import com.paddleup.scoring.dto.MatchPlayersDto;
import com.paddleup.scoring.dto.SaveSheetRequest;
import com.paddleup.scoring.dto.SheetResultDto;
import com.paddleup.scoring.dto.StatMatchDto;
import com.paddleup.scoring.dto.StatSheetViewDto;
import com.paddleup.scoring.service.ManualStatsService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual stat entry for a room. Every route is for the Commissioner only: a non-member gets 404 and
 * a member who is not the Commissioner 403. The server decides from its own clock whether a match
 * has started.
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/stat-entry")
class ManualStatsController {

  private final CurrentUser currentUser;
  private final ManualStatsService manual;

  ManualStatsController(CurrentUser currentUser, ManualStatsService manual) {
    this.currentUser = currentUser;
    this.manual = manual;
  }

  @GetMapping("/matches")
  List<StatMatchDto> matches(@PathVariable UUID roomId) {
    return manual.matches(roomId, currentUser.require());
  }

  @GetMapping("/matches/{matchId}/players")
  MatchPlayersDto matchPlayers(@PathVariable UUID roomId, @PathVariable String matchId) {
    return manual.matchPlayers(roomId, currentUser.require(), matchId);
  }

  @GetMapping("/matches/{matchId}/players/{poolPlayerId}")
  StatSheetViewDto sheet(
      @PathVariable UUID roomId, @PathVariable String matchId, @PathVariable UUID poolPlayerId) {
    return manual.sheet(roomId, currentUser.require(), matchId, poolPlayerId);
  }

  @PutMapping(
      value = "/matches/{matchId}/players/{poolPlayerId}",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  SheetResultDto save(
      @PathVariable UUID roomId,
      @PathVariable String matchId,
      @PathVariable UUID poolPlayerId,
      @RequestBody SaveSheetRequest request) {
    return manual.save(roomId, currentUser.require(), matchId, poolPlayerId, request);
  }

  @PostMapping(
      value = "/matches/{matchId}/players/{poolPlayerId}/clear",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  SheetResultDto clear(
      @PathVariable UUID roomId,
      @PathVariable String matchId,
      @PathVariable UUID poolPlayerId,
      @RequestBody ClearFieldsRequest request) {
    return manual.clear(roomId, currentUser.require(), matchId, poolPlayerId, request);
  }
}
