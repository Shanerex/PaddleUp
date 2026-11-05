package com.paddleup.packs.dto;

import java.util.List;
import java.util.UUID;

/** One immutable dataset version with its players and the order of its sets. */
public record SeasonDatasetDto(
    UUID id,
    String packId,
    int version,
    String packVersion,
    String sourceFormat,
    java.time.Instant importedAt,
    int playerCount,
    int rejectedCount,
    List<String> setOrder,
    List<SeasonPlayerDto> players) {

  public SeasonDatasetDto {
    setOrder = List.copyOf(setOrder);
    players = List.copyOf(players);
  }
}
