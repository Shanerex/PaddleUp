package com.paddleup.rooms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A whole-config save. {@code version} is the config version the editor started from. */
public record SaveConfigRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int version,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RoomConfigDto config) {}
