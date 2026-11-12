package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.paddleup.packs.dto.RuleSetDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A room's rule set as a member sees it. {@code status} is {@code pack-default} or {@code custom};
 * {@code version} is 0 until the first change and is what a write must send back. {@code freezesAt}
 * is null when the room has no first match yet.
 */
public record RuleSetViewDto(
    @Schema(requiredMode = REQUIRED) RuleSetDto rules,
    @Schema(requiredMode = REQUIRED) String status,
    @Schema(requiredMode = REQUIRED) int version,
    @Schema(requiredMode = REQUIRED, description = "The pack version the default was taken from")
        String packVersion,
    @Schema(requiredMode = REQUIRED) boolean frozen,
    @Schema(nullable = true) Instant freezesAt) {

  public static final String PACK_DEFAULT = "pack-default";
  public static final String CUSTOM = "custom";
}
