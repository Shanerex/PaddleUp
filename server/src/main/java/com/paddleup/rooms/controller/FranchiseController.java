package com.paddleup.rooms.controller;

import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.rooms.dto.FranchiseDto;
import com.paddleup.rooms.dto.FranchiseRequest;
import com.paddleup.rooms.dto.MemberDto;
import com.paddleup.rooms.service.FranchiseService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{id}")
class FranchiseController {

  private final CurrentUser currentUser;
  private final FranchiseService franchises;

  FranchiseController(CurrentUser currentUser, FranchiseService franchises) {
    this.currentUser = currentUser;
    this.franchises = franchises;
  }

  @GetMapping("/members")
  List<MemberDto> members(@PathVariable UUID id) {
    return franchises.members(currentUser.require(), id);
  }

  /** The Commissioner's own franchise, created without a code. */
  @PostMapping(value = "/franchises", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  FranchiseDto createOwn(@PathVariable UUID id, @RequestBody FranchiseRequest request) {
    return franchises.createOwn(currentUser.require(), id, request);
  }

  /** Removes a manager; answers with the members who remain. */
  @DeleteMapping("/franchises/{franchiseId}")
  List<MemberDto> remove(@PathVariable UUID id, @PathVariable UUID franchiseId) {
    return franchises.remove(currentUser.require(), id, franchiseId);
  }

  @PatchMapping(value = "/franchises/{franchiseId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  FranchiseDto edit(
      @PathVariable UUID id,
      @PathVariable UUID franchiseId,
      @RequestBody FranchiseRequest request) {
    return franchises.edit(currentUser.require(), id, franchiseId, request);
  }
}
