package com.paddleup.rooms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Starts a room from one preset of one pack. {@code competition} is optional. */
public record CreateRoomRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String packId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String presetId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    String competition) {}
