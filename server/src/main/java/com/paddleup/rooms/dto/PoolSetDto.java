package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** One auction set in play order, with the number of players it holds (0 is possible). */
public record PoolSetDto(
    @Schema(requiredMode = REQUIRED) String name, @Schema(requiredMode = REQUIRED) int players) {}
