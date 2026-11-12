package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.paddleup.packs.dto.RuleSetDto;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A whole-set edit. {@code version} is the rule set version the editor started from; the controller
 * refuses null.
 */
public record EditRulesRequest(
    @Schema(requiredMode = REQUIRED) Integer version,
    @Schema(requiredMode = REQUIRED) RuleSetDto rules) {}
