package com.paddleup.scoring.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * One entered value of a save. A null {@code value} is a blank field and is left as it is. A null
 * {@code eventType} means the stat itself.
 */
public record FieldValueDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String stat,
    @Schema(nullable = true) String eventType,
    @Schema(nullable = true) BigDecimal value) {}
