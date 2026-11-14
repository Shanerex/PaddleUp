package com.paddleup.scoring.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One player's effective stats in one match. {@code stats} maps a stat id to its value; {@code
 * events} maps a stat id to a count per declared event type. A stat or event type that is absent
 * counts as 0.
 */
public record StatLine(Map<String, BigDecimal> stats, Map<String, Map<String, BigDecimal>> events) {

  public static final StatLine EMPTY = new StatLine(Map.of(), Map.of());

  public StatLine {
    stats = stats == null ? Map.of() : Map.copyOf(stats);
    events = events == null ? Map.of() : Map.copyOf(events);
  }

  public BigDecimal stat(String id) {
    return stats.getOrDefault(id, BigDecimal.ZERO);
  }

  public BigDecimal event(String stat, String eventType) {
    return events.getOrDefault(stat, Map.of()).getOrDefault(eventType, BigDecimal.ZERO);
  }

  public boolean isEmpty() {
    return stats.isEmpty() && events.isEmpty();
  }
}
