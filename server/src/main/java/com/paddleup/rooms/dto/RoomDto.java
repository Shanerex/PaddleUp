package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A room as one of its members sees it. {@code myRole} is {@code commissioner} or {@code manager};
 * {@code advanceTo} lists the states the Commissioner may move to through REST right now.
 */
public record RoomDto(
    @Schema(requiredMode = REQUIRED) UUID id,
    @Schema(requiredMode = REQUIRED) RoomState state,
    @Schema(requiredMode = REQUIRED) String myRole,
    @Schema(requiredMode = REQUIRED) UserRefDto commissioner,
    @Schema(requiredMode = REQUIRED) int configVersion,
    @Schema(requiredMode = REQUIRED) RoomConfigDto config,
    @Schema(requiredMode = REQUIRED) PackLabelsDto packLabels,
    @Schema(requiredMode = REQUIRED) int franchiseCount,
    @Schema(nullable = true) UUID myFranchiseId,
    @Schema(requiredMode = REQUIRED) List<RoomState> advanceTo,
    @Schema(requiredMode = REQUIRED) Instant createdAt) {}
