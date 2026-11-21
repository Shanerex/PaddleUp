package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The whole pool for one match, players with a value first. {@code withValues} of {@code poolSize}
 * players have a value (the "n of N players" count).
 */
public record MatchPlayersDto(
    @Schema(requiredMode = REQUIRED) StatMatchDto match,
    @Schema(requiredMode = REQUIRED) int withValues,
    @Schema(requiredMode = REQUIRED) int poolSize,
    @Schema(requiredMode = REQUIRED) List<StatMatchPlayerDto> players) {}
