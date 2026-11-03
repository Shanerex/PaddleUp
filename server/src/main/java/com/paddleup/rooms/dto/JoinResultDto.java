package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** The room joined and the caller's franchise there. */
public record JoinResultDto(
    @Schema(requiredMode = REQUIRED) UUID roomId,
    @Schema(requiredMode = REQUIRED) FranchiseDto franchise) {}
