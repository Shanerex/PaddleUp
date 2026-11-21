package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One player's stat sheet for one match: every field of the pack's stat schema with the provider
 * and manual value. {@code points} is the stored total (null when none), {@code providerOnly} what
 * the provider values alone would give (null when the player has no provider data).
 */
public record StatSheetViewDto(
    @Schema(requiredMode = REQUIRED) StatMatchDto match,
    @Schema(requiredMode = REQUIRED) UUID poolPlayerId,
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(requiredMode = REQUIRED, description = "The role id; the room's pack gives its label")
        String role,
    @Schema(nullable = true) OwnerDto owner,
    @Schema(nullable = true) BigDecimal points,
    @Schema(nullable = true) BigDecimal providerOnly,
    @Schema(requiredMode = REQUIRED) List<SheetFieldDto> fields) {}
