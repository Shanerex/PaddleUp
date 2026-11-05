package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A room's pool as the caller sees it. {@code hasPool} is false until the room has a copy; then
 * {@code version} is 0 and the lists are empty. {@code version} is the compare-and-set version that
 * every write must send back. {@code datasetVersion} is the season data the copy came from; {@code
 * latestDatasetVersion} is the newest one for the pack, or null when none exists. {@code canEdit}
 * is true only for the Commissioner before the auction.
 */
public record PoolDto(
    @Schema(requiredMode = REQUIRED) boolean hasPool,
    @Schema(requiredMode = REQUIRED) int version,
    @Schema(nullable = true) Integer datasetVersion,
    @Schema(nullable = true) Integer latestDatasetVersion,
    @Schema(requiredMode = REQUIRED) int editCount,
    @Schema(requiredMode = REQUIRED) boolean canEdit,
    @Schema(requiredMode = REQUIRED) List<PoolPlayerDto> players,
    @Schema(requiredMode = REQUIRED) List<PoolSetDto> sets) {

  public PoolDto {
    players = List.copyOf(players);
    sets = List.copyOf(sets);
  }
}
