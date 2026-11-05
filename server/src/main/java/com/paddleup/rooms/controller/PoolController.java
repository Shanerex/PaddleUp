package com.paddleup.rooms.controller;

import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.rooms.dto.PoolDto;
import com.paddleup.rooms.dto.PoolPlayerRequest;
import com.paddleup.rooms.dto.PoolResetRequest;
import com.paddleup.rooms.dto.PoolSetsRequest;
import com.paddleup.rooms.service.PoolService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A room's player pool: any member reads; only the Commissioner writes, before the auction. */
@RestController
@RequestMapping("/api/rooms/{id}/pool")
class PoolController {

  private final CurrentUser currentUser;
  private final PoolService pool;

  PoolController(CurrentUser currentUser, PoolService pool) {
    this.currentUser = currentUser;
    this.pool = pool;
  }

  @GetMapping
  PoolDto get(@PathVariable UUID id) {
    return pool.get(currentUser.require(), id);
  }

  @PostMapping(value = "/players", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  PoolDto add(@PathVariable UUID id, @RequestBody PoolPlayerRequest request) {
    return pool.add(currentUser.require(), id, request);
  }

  @PutMapping(value = "/players/{playerId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  PoolDto edit(
      @PathVariable UUID id, @PathVariable UUID playerId, @RequestBody PoolPlayerRequest request) {
    return pool.edit(currentUser.require(), id, playerId, request);
  }

  @DeleteMapping("/players/{playerId}")
  PoolDto remove(@PathVariable UUID id, @PathVariable UUID playerId, @RequestParam int version) {
    return pool.remove(currentUser.require(), id, playerId, version);
  }

  @PostMapping(value = "/reset", consumes = MediaType.APPLICATION_JSON_VALUE)
  PoolDto reset(@PathVariable UUID id, @RequestBody PoolResetRequest request) {
    return pool.reset(currentUser.require(), id, request);
  }

  @PutMapping(value = "/sets", consumes = MediaType.APPLICATION_JSON_VALUE)
  PoolDto saveSets(@PathVariable UUID id, @RequestBody PoolSetsRequest request) {
    return pool.saveSetOrder(currentUser.require(), id, request);
  }
}
