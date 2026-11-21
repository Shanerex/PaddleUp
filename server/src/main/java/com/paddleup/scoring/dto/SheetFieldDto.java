package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * One entry field of a stat sheet, taken from the room's pack stat schema. {@code eventType} is ""
 * for the stat itself and names the event type for a typed count. {@code min} and {@code max} are
 * the limits in force (the stat's own, else 0 to 1,000,000). {@code input} is {@code number} or
 * {@code yes_no}. {@code provider} and {@code manual} are the values stored for the player and
 * match, null when absent.
 */
public record SheetFieldDto(
    @Schema(requiredMode = REQUIRED) String stat,
    @Schema(requiredMode = REQUIRED) String eventType,
    @Schema(requiredMode = REQUIRED) String label,
    @Schema(requiredMode = REQUIRED) String kind,
    @Schema(nullable = true) String unit,
    @Schema(nullable = true) String group,
    @Schema(requiredMode = REQUIRED) BigDecimal min,
    @Schema(requiredMode = REQUIRED) BigDecimal max,
    @Schema(requiredMode = REQUIRED) String input,
    @Schema(nullable = true) BigDecimal provider,
    @Schema(nullable = true) BigDecimal manual) {

  public SheetFieldDto withValues(BigDecimal provider, BigDecimal manual) {
    return new SheetFieldDto(
        stat, eventType, label, kind, unit, group, min, max, input, provider, manual);
  }
}
