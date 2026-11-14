package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.paddleup.packs.dto.RuleSetDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One entry of a room's rule change log. {@code before} and {@code after} are the whole sets. */
public record RuleLogEntryDto(
    @Schema(requiredMode = REQUIRED) long seq,
    @Schema(requiredMode = REQUIRED, description = "edit, revert or post_freeze_change")
        String kind,
    @Schema(requiredMode = REQUIRED) UUID actor,
    @Schema(requiredMode = REQUIRED) Instant occurredAt,
    @Schema(nullable = true) String reason,
    @Schema(requiredMode = REQUIRED) boolean attested,
    @Schema(requiredMode = REQUIRED) RuleSetDto before,
    @Schema(requiredMode = REQUIRED) RuleSetDto after,
    @Schema(requiredMode = REQUIRED) RuleDiffDto diff) {}
