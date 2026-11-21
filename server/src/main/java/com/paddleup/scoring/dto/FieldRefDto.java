package com.paddleup.scoring.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A stat and event type named by a clear. A null {@code eventType} means the stat itself. */
public record FieldRefDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String stat,
    @Schema(nullable = true) String eventType) {}
