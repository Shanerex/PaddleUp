package com.paddleup.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** A franchise's purse left and the players it holds so far. */
public record StandingDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID franchiseId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long purse,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SquadEntryDto> squad) {}
