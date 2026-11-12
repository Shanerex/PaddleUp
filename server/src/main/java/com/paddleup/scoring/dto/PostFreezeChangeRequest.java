package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.paddleup.packs.dto.RuleSetDto;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A change after the freeze. The Commissioner gives a reason (1 to 500 characters) and attests that
 * every manager agreed; both are required and both are logged.
 */
public record PostFreezeChangeRequest(
    @Schema(requiredMode = REQUIRED) Integer version,
    @Schema(requiredMode = REQUIRED) RuleSetDto rules,
    @Schema(requiredMode = REQUIRED) String reason,
    @Schema(requiredMode = REQUIRED) boolean attested) {}
