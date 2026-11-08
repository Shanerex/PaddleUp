package com.paddleup.packs.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One line of a pack's version list: what a dataset version holds, without its players. */
public record DatasetSummaryDto(
    @Schema(requiredMode = REQUIRED) UUID id,
    @Schema(requiredMode = REQUIRED) int version,
    @Schema(requiredMode = REQUIRED) String packVersion,
    @Schema(requiredMode = REQUIRED) String sourceFormat,
    @Schema(requiredMode = REQUIRED) Instant importedAt,
    @Schema(requiredMode = REQUIRED) int playerCount,
    @Schema(requiredMode = REQUIRED) int rejectedCount) {}
