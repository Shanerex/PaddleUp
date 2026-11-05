package com.paddleup.packs.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One broken rule on one player field. {@code field} is the dataset column name ({@code name},
 * {@code country}, {@code role}, {@code tags}, {@code base_price}, {@code set}, {@code
 * provider_id}); {@code value} is the offending input, cut short; {@code message} is shown to a
 * person.
 */
public record FieldError(
    @Schema(requiredMode = REQUIRED) String field,
    @Schema(requiredMode = REQUIRED) String value,
    @Schema(requiredMode = REQUIRED) String message) {}
