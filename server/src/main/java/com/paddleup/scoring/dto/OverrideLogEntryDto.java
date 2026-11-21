package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry of a room's override log. {@code oldValue} is null for a first value, {@code newValue}
 * for a clear.
 */
public record OverrideLogEntryDto(
    @Schema(requiredMode = REQUIRED) long seq,
    @Schema(requiredMode = REQUIRED) UUID actor,
    @Schema(requiredMode = REQUIRED) Instant occurredAt,
    @Schema(requiredMode = REQUIRED) String matchId,
    @Schema(requiredMode = REQUIRED) UUID poolPlayerId,
    @Schema(requiredMode = REQUIRED) String stat,
    @Schema(requiredMode = REQUIRED) String eventType,
    @Schema(nullable = true) BigDecimal oldValue,
    @Schema(nullable = true) BigDecimal newValue) {}
