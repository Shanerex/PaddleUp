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

  /**
   * Room configs may hold bad entries (see the room validator), so only a named role with a minimum
   * above zero and a named quota with a non-negative cap are kept: nothing malformed reaches the
   * go-live event or a replay.
   */
  public SquadRules {
    // Sorted: jsonb does not keep key order, so live state and replay must agree.
    Map<String, Integer> minimums = new TreeMap<>();
    roleMinimums.forEach(
        (role, least) -> {
          if (role != null && least != null && least > 0) {
            minimums.put(role, least);
          }
        });
    roleMinimums = Collections.unmodifiableMap(minimums);
    quotas =
        quotas.stream()
            .filter(q -> q != null && q.category() != null && q.tag() != null && q.max() >= 0)
            .toList();
  }
}
