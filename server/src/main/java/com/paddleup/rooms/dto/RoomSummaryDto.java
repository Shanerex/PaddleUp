package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One room on the caller's home hub. {@code myFranchise} is null for an admin-only Commissioner;
 * {@code packName} is null when the pack has no display name. {@code auctionStartsAt} is set only
 * for a room in {@code auction_scheduled} that has a start; {@code serverNow} is the server clock
 * when the list was built, the same on every item.
 */
public record RoomSummaryDto(
    @Schema(requiredMode = REQUIRED) UUID id,
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(requiredMode = REQUIRED) String competition,
    @Schema(requiredMode = REQUIRED) RoomState state,
    @Schema(requiredMode = REQUIRED) String packId,
    @Schema(nullable = true) String packName,
    @Schema(requiredMode = REQUIRED) boolean commissioner,
    @Schema(requiredMode = REQUIRED) int franchiseCount,
    @Schema(requiredMode = REQUIRED) int minManagers,
    @Schema(requiredMode = REQUIRED) int maxManagers,
    @Schema(nullable = true) FranchiseDto myFranchise,
    @Schema(requiredMode = REQUIRED) Instant updatedAt,
    @Schema(nullable = true) Instant auctionStartsAt,
    @Schema(requiredMode = REQUIRED) Instant serverNow) {}
