package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Every player's stored points for one match of a room. */
public record MatchPointsDto(
    @Schema(requiredMode = REQUIRED) String matchId,
    @Schema(requiredMode = REQUIRED) List<PlayerPointsDto> players) {}
