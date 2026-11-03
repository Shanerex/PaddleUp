package com.paddleup.rooms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Asks to move the room to this state. */
public record TransitionRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RoomState to) {}
