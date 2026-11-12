package com.paddleup.scoring;

import com.paddleup.scoring.model.StatLine;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** A tiny builder for effective stat lines in scoring tests. */
public final class StatLines {

  private final Map<String, BigDecimal> stats = new HashMap<>();
  private final Map<String, Map<String, BigDecimal>> events = new HashMap<>();

  private StatLines() {}

  public static StatLines of() {
    return new StatLines();
  }

  public StatLines stat(String id, String value) {
    stats.put(id, new BigDecimal(value));
    return this;
  }

  public StatLines stat(String id, long value) {
    return stat(id, Long.toString(value));
  }

  public StatLines event(String stat, String type, long count) {
    events.computeIfAbsent(stat, k -> new HashMap<>()).put(type, BigDecimal.valueOf(count));
    return this;
  }

  public StatLine build() {
    return new StatLine(stats, events);
  }
}
