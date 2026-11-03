package com.paddleup.packs.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One player's stats in a match. {@code stats} maps stat id to its value; {@code events} maps stat
 * id to a count per declared event type. Manual entry reuses this type.
 */
public record StatLineDto(
    String playerId, Map<String, BigDecimal> stats, Map<String, Map<String, Integer>> events) {
  public StatLineDto {
    stats = stats == null ? Map.of() : Map.copyOf(stats);
    events = events == null ? Map.of() : Map.copyOf(events);
  }
}
