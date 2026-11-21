package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One match of the room's season dataset. {@code n} is its place in play order (start, then id).
 * {@code chip} is UPCOMING, NO_DATA, MANUAL, PROVIDER_MANUAL or PROVIDER; {@code manualPlayers} is
 * the number of players with a manual value.
 */
public record StatMatchDto(
    @Schema(requiredMode = REQUIRED) String matchId,
    @Schema(requiredMode = REQUIRED) int n,
    @Schema(nullable = true) String label,
    @Schema(requiredMode = REQUIRED) Instant startsAt,
    @Schema(requiredMode = REQUIRED) String chip,
    @Schema(requiredMode = REQUIRED) int manualPlayers) {}
