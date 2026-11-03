package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** One broken config rule: the field it concerns, and a message for the Commissioner. */
public record Violation(
    @Schema(requiredMode = REQUIRED) String field,
    @Schema(requiredMode = REQUIRED) String message) {}
