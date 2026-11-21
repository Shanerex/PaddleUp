package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * The result of a save or clear: the player's points before and after (null when the player had
 * none), what the provider values alone give, the owning franchise's change (null for an unowned
 * player), the number of fields that changed and the sheet as it now stands.
 */
public record SheetResultDto(
    @Schema(nullable = true) BigDecimal before,
    @Schema(nullable = true) BigDecimal after,
    @Schema(nullable = true) BigDecimal providerOnly,
    @Schema(nullable = true) FranchiseDeltaDto franchise,
    @Schema(requiredMode = REQUIRED) int changedFields,
    @Schema(requiredMode = REQUIRED) StatSheetViewDto sheet) {}
