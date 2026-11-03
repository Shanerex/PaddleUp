package com.paddleup.rooms.controller;

import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.rooms.dto.CreateRoomRequest;
import com.paddleup.rooms.dto.PackChoiceDto;
import com.paddleup.rooms.dto.RoomDto;
import com.paddleup.rooms.dto.RoomSummaryDto;
import com.paddleup.rooms.dto.SaveConfigRequest;
import com.paddleup.rooms.service.RoomService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
class RoomController {

  private final CurrentUser currentUser;
  private final RoomService rooms;

  RoomController(CurrentUser currentUser, RoomService rooms) {
    this.currentUser = currentUser;
    this.rooms = rooms;
  }

  /** The caller's rooms for the home hub, most recently changed first. */
  @GetMapping
  List<RoomSummaryDto> mine() {
    return rooms.listMine(currentUser.require());
  }

  /** The packs a new room can start from. */
  @GetMapping("/packs")
  List<PackChoiceDto> packs() {
    currentUser.require();
    return rooms.packChoices();
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  RoomDto create(@RequestBody CreateRoomRequest request) {
    return rooms.create(currentUser.require(), request);
  }

  @GetMapping("/{id}")
  RoomDto get(@PathVariable UUID id) {
    return rooms.get(currentUser.require(), id);
  }

  @PutMapping(value = "/{id}/config", consumes = MediaType.APPLICATION_JSON_VALUE)
  RoomDto saveConfig(@PathVariable UUID id, @RequestBody SaveConfigRequest request) {
    return rooms.saveConfig(currentUser.require(), id, request);
  }
}
