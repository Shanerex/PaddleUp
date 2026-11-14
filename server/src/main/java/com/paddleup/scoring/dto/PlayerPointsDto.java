package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** One pool player's points in a match with the rule lines that made them. */
public record PlayerPointsDto(
    @Schema(requiredMode = REQUIRED) UUID poolPlayerId,
    @Schema(requiredMode = REQUIRED) BigDecimal total,
    @Schema(requiredMode = REQUIRED) List<PointsLineDto> lines,
    @Schema(requiredMode = REQUIRED, description = "The rule set version the points came from")
        int ruleSetVersion) {}
