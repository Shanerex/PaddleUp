package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/** The change in an owning franchise's points for the match caused by a save or clear. */
public record FranchiseDeltaDto(
    @Schema(requiredMode = REQUIRED) UUID id, @Schema(requiredMode = REQUIRED) BigDecimal delta) {}
