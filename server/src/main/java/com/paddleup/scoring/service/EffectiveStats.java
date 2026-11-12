package com.paddleup.scoring.service;

import com.paddleup.rooms.dto.ScoringPlayerDto;
import com.paddleup.scoring.model.OverrideValue;
import com.paddleup.scoring.model.ProviderValue;
import com.paddleup.scoring.model.StatLine;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Works out the stat line each pool player is scored on: a manual override beats the provider value
 * for the same stat and event type, and everything else falls back to the provider value of the
 * dataset player the pool player was copied from. A player the Commissioner added by hand has no
 * dataset player, so only manual values count. Pure.
 */
final class EffectiveStats {

  /** The stat itself, as opposed to the count of one event type. */
  static final String NO_EVENT = "";

  private EffectiveStats() {}

  /**
   * The effective line of every pool player that has at least one value; a player with none is left
   * out, so a match nobody has stats for scores nothing.
   */
  static Map<UUID, StatLine> forPlayers(
      List<ScoringPlayerDto> players, List<ProviderValue> provider, List<OverrideValue> overrides) {
    Map<UUID, List<ProviderValue>> bySeason = new HashMap<>();
    for (ProviderValue value : provider) {
      bySeason.computeIfAbsent(value.seasonPlayerId(), k -> new java.util.ArrayList<>()).add(value);
    }
    Map<UUID, List<OverrideValue>> byPool = new HashMap<>();
    for (OverrideValue value : overrides) {
      byPool.computeIfAbsent(value.poolPlayerId(), k -> new java.util.ArrayList<>()).add(value);
    }
    Map<UUID, StatLine> lines = new LinkedHashMap<>();
    for (ScoringPlayerDto player : players) {
      List<ProviderValue> fromProvider =
          player.sourcePlayerId() == null
              ? List.of()
              : bySeason.getOrDefault(player.sourcePlayerId(), List.of());
      List<OverrideValue> manual = byPool.getOrDefault(player.poolPlayerId(), List.of());
      if (fromProvider.isEmpty() && manual.isEmpty()) {
        continue;
      }
      lines.put(player.poolPlayerId(), merge(fromProvider, manual));
    }
    return lines;
  }

  private static StatLine merge(List<ProviderValue> provider, List<OverrideValue> manual) {
    Map<String, BigDecimal> stats = new HashMap<>();
    Map<String, Map<String, BigDecimal>> events = new HashMap<>();
    for (ProviderValue v : provider) {
      put(stats, events, v.stat(), v.eventType(), v.value());
    }
    // Applied last, so for the same stat and event type the manual value wins.
    for (OverrideValue v : manual) {
      put(stats, events, v.stat(), v.eventType(), v.value());
    }
    return new StatLine(stats, events);
  }

  private static void put(
      Map<String, BigDecimal> stats,
      Map<String, Map<String, BigDecimal>> events,
      String stat,
      String eventType,
      BigDecimal value) {
    if (NO_EVENT.equals(eventType)) {
      stats.put(stat, value);
    } else {
      events.computeIfAbsent(stat, k -> new HashMap<>()).put(eventType, value);
    }
  }
}
