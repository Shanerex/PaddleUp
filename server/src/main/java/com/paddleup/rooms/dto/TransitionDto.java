package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** One state change of a room, with who made it and when (UTC). */
public record TransitionDto(
    @Schema(requiredMode = REQUIRED) RoomState from,
    @Schema(requiredMode = REQUIRED) RoomState to,
    @Schema(nullable = true) UserRefDto actor,
    @Schema(requiredMode = REQUIRED) Instant occurredAt) {}
