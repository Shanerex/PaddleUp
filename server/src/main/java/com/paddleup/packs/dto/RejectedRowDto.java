package com.paddleup.packs.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** One problem in one rejected row of an import; a row with several problems has several. */
public record RejectedRowDto(
    @Schema(requiredMode = REQUIRED) int row,
    @Schema(requiredMode = REQUIRED) String field,
    @Schema(requiredMode = REQUIRED) String value,
    @Schema(requiredMode = REQUIRED) String message) {}
