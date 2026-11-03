package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * What a valid code leads to: the room's name, the colours already taken, whether the room is full
 * and whether the caller already has a franchise there ({@code roomId} is set only then), plus the
 * pack name, competition and seat counts for the join screen. The caller already holds a valid
 * code.
 */
public record JoinPreviewDto(
    @Schema(requiredMode = REQUIRED) String roomName,
    @Schema(requiredMode = REQUIRED) List<String> takenColours,
    @Schema(requiredMode = REQUIRED) boolean full,
    @Schema(requiredMode = REQUIRED) boolean alreadyJoined,
    @Schema(nullable = true) UUID roomId,
    @Schema(nullable = true) String packName,
    @Schema(requiredMode = REQUIRED) String competition,
    @Schema(requiredMode = REQUIRED) int franchiseCount,
    @Schema(requiredMode = REQUIRED) int maxManagers) {}
