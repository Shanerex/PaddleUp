package com.paddleup.auction.util;

import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.SquadRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The purse a franchise must keep to finish a legal squad, as a pure function. If it wins the open
 * lot, the cheapest base prices in the remaining pool fill each unmet role minimum, then the rest
 * of the squad minimum. The lot's own role and tags count as already won, and a filler that would
 * break a restricted category's cap is skipped. The pick is greedy (cheapest first, roles in id
 * order), so with caps it is an estimate, never a promise of the best squad.
 */
public final class SquadReserve {

  /** Raised cost when the remaining pool cannot fill the squad at all. */
  public static final long UNFILLABLE = Long.MAX_VALUE;

  private SquadReserve() {}

  /**
   * The reserve: {@code cost} in the smallest currency unit ({@link #UNFILLABLE} when the pool
   * cannot supply the players), and {@code role}, the pack role behind most of it (the one the pool
   * lacks when unfillable), or null when only the squad minimum is unmet.
   */
  public record Result(long cost, String role) {}

  /**
   * @param held the lots the franchise already holds
   * @param lot the open lot, counted as won
   * @param remaining the lots not yet auctioned, excluding {@code lot}
   */
  public static Result of(SquadRules rules, List<Lot> held, Lot lot, List<Lot> remaining) {
    List<Lot> squad = new ArrayList<>(held);
    squad.add(lot);
    Map<String, Integer> capacity = new HashMap<>();
    for (SquadRules.Quota q : rules.quotas()) {
      long used = squad.stream().filter(l -> l.tags().contains(q.tag())).count();
      capacity.put(q.category(), (int) Math.max(0, q.max() - used));
    }
    List<Lot> pool = new ArrayList<>(remaining);
    pool.sort(Comparator.comparingLong(Lot::basePrice).thenComparingInt(Lot::no));

    long total = 0;
    int picked = 0;
    Map<String, Long> byRole = new TreeMap<>();
    for (var entry : rules.roleMinimums().entrySet()) {
      String role = entry.getKey();
      int need = entry.getValue() - (int) squad.stream().filter(l -> role.equals(l.role())).count();
      for (int i = 0; i < need; i++) {
        Lot pick = take(pool, rules, capacity, l -> role.equals(l.role()));
        if (pick == null) {
          return new Result(UNFILLABLE, role);
        }
        total += pick.basePrice();
        byRole.merge(role, pick.basePrice(), Long::sum);
        picked++;
      }
    }
    int rest = rules.min() - squad.size() - picked;
    for (int i = 0; i < rest; i++) {
      Lot pick = take(pool, rules, capacity, l -> true);
      if (pick == null) {
        return new Result(UNFILLABLE, null);
      }
      total += pick.basePrice();
    }
    String heaviest =
        byRole.entrySet().stream()
            .max(Map.Entry.<String, Long>comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    return new Result(total, heaviest);
  }

  /** Removes and returns the cheapest eligible lot, taking one place in each category it has. */
  private static Lot take(
      List<Lot> pool,
      SquadRules rules,
      Map<String, Integer> capacity,
      java.util.function.Predicate<Lot> wanted) {
    for (int i = 0; i < pool.size(); i++) {
      Lot candidate = pool.get(i);
      if (!wanted.test(candidate) || !fits(candidate, rules, capacity)) {
        continue;
      }
      for (SquadRules.Quota q : rules.quotas()) {
        if (candidate.tags().contains(q.tag())) {
          capacity.merge(q.category(), -1, Integer::sum);
        }
      }
      return pool.remove(i);
    }
    return null;
  }

  private static boolean fits(Lot candidate, SquadRules rules, Map<String, Integer> capacity) {
    for (SquadRules.Quota q : rules.quotas()) {
      if (candidate.tags().contains(q.tag()) && capacity.getOrDefault(q.category(), 0) <= 0) {
        return false;
      }
    }
    return true;
  }
}
