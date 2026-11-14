package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** One rule that fired for a player in a match, and the points it gave. */
public record PointsLineDto(
    @Schema(requiredMode = REQUIRED) String ruleId,
    @Schema(requiredMode = REQUIRED) String label,
    @Schema(requiredMode = REQUIRED) BigDecimal points) {}
