package com.paddleup.scoring.controller;

import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.scoring.dto.EditRulesRequest;
import com.paddleup.scoring.dto.PostFreezeChangeRequest;
import com.paddleup.scoring.dto.RevertRequest;
import com.paddleup.scoring.dto.RuleLogEntryDto;
import com.paddleup.scoring.dto.RuleSetViewDto;
import com.paddleup.scoring.exception.InvalidRequestException;
import com.paddleup.scoring.service.RuleSetService;
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
 * A room's scoring rules through REST. Any member reads; only the Commissioner writes, and the
 * server decides whether the rules are frozen from its own clock.
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/scoring/rules")
class RuleSetController {

  private final CurrentUser currentUser;
  private final RuleSetService rules;

  RuleSetController(CurrentUser currentUser, RuleSetService rules) {
    this.currentUser = currentUser;
    this.rules = rules;
  }

  @GetMapping
  RuleSetViewDto readRules(@PathVariable UUID roomId) {
    return rules.read(roomId, currentUser.require());
  }

  @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  RuleSetViewDto editRules(@PathVariable UUID roomId, @RequestBody EditRulesRequest request) {
    return rules.edit(roomId, currentUser.require(), version(request.version()), request.rules());
  }

  @PostMapping(value = "/revert", consumes = MediaType.APPLICATION_JSON_VALUE)
  RuleSetViewDto revertRules(@PathVariable UUID roomId, @RequestBody RevertRequest request) {
    return rules.revert(roomId, currentUser.require(), version(request.version()));
  }

  @PostMapping(value = "/post-freeze-change", consumes = MediaType.APPLICATION_JSON_VALUE)
  RuleSetViewDto changeRulesAfterFreeze(
      @PathVariable UUID roomId, @RequestBody PostFreezeChangeRequest request) {
    return rules.changeAfterFreeze(
        roomId,
        currentUser.require(),
        version(request.version()),
        request.rules(),
        request.reason(),
        request.attested());
  }

  /** The version the editor started from; a body without one is refused, never read as 0. */
  private static int version(Integer version) {
    if (version == null) {
      throw new InvalidRequestException("The request needs the rule set version you started from");
    }
    return version;
  }

  @GetMapping("/log")
  List<RuleLogEntryDto> ruleLog(@PathVariable UUID roomId) {
    return rules.log(roomId, currentUser.require());
  }
}
