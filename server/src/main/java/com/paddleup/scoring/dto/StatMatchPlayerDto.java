package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One pool player in a match's list. {@code chip} is PROVIDER, PROVIDER_MANUAL or MANUAL, null when
 * the player has no value. {@code total} is the stored points, null when the player has none.
 */
public record StatMatchPlayerDto(
    @Schema(requiredMode = REQUIRED) UUID poolPlayerId,
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(requiredMode = REQUIRED, description = "The role id; the room's pack gives its label")
        String role,
    @Schema(nullable = true) OwnerDto owner,
    @Schema(nullable = true) String chip,
    @Schema(nullable = true) BigDecimal total) {}
