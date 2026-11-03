package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A manager's team. {@code colour} is one of the 12 design hue keys. */
public record FranchiseDto(
    @Schema(requiredMode = REQUIRED) UUID id,
    @Schema(requiredMode = REQUIRED) UUID ownerUserId,
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(requiredMode = REQUIRED) String colour,
    @Schema(requiredMode = REQUIRED) String monogram) {}
