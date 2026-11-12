package com.paddleup.auction.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The squad rules frozen into the go-live event: how many players a franchise may hold, the minimum
 * per role, and a cap per restricted category (the players carrying {@code tag}). Role and category
 * ids come from the room's pack; the engine never names one.
 */
public record SquadRules(int min, int max, Map<String, Integer> roleMinimums, List<Quota> quotas) {

  /** At most {@code max} squad players may carry {@code tag}. */
  public record Quota(String category, String tag, int max) {}

  public SquadRules {
    // Sorted: jsonb does not keep key order, so live state and replay must agree.
    roleMinimums = Collections.unmodifiableMap(new TreeMap<>(roleMinimums));
    quotas = List.copyOf(quotas);
  }
}
