package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Why one field of a sheet was refused, with the range that applies. {@code reason} is {@code
 * unknown_stat}, {@code unknown_event_type}, {@code duplicate_field}, {@code too_many_decimals},
 * {@code not_whole}, {@code below_min} or {@code above_max}. {@code min} and {@code max} are null
 * for a field that names no known stat.
 */
public record SheetFieldErrorDto(
    @Schema(requiredMode = REQUIRED) String stat,
    @Schema(requiredMode = REQUIRED) String eventType,
    @Schema(nullable = true) BigDecimal min,
    @Schema(nullable = true) BigDecimal max,
    @Schema(requiredMode = REQUIRED) String reason) {}
