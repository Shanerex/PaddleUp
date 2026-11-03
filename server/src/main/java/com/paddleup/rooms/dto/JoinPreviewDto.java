package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * What a valid code leads to: the room's name, the colours already taken, whether the room is full
 * and whether the caller already has a franchise there ({@code roomId} is set only then).
 */
public record JoinPreviewDto(
    @Schema(requiredMode = REQUIRED) String roomName,
    @Schema(requiredMode = REQUIRED) List<String> takenColours,
    @Schema(requiredMode = REQUIRED) boolean full,
    @Schema(requiredMode = REQUIRED) boolean alreadyJoined,
    @Schema(nullable = true) UUID roomId) {}
