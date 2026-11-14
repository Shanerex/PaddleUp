package com.paddleup.scoring.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * One dataset player's provider stats in a match. {@code stats} maps a stat id to its value; {@code
 * events} maps a stat id to a count per event type.
 */
public record ProviderStatLineDto(
    UUID seasonPlayerId,
    Map<String, BigDecimal> stats,
    Map<String, Map<String, BigDecimal>> events) {

  public ProviderStatLineDto {
    stats = stats == null ? Map.of() : Map.copyOf(stats);
    events = events == null ? Map.of() : Map.copyOf(events);
  }
}
