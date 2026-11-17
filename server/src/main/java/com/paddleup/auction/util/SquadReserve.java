package com.paddleup.auction.util;

import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.SquadRules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The purse a franchise must keep to finish a legal squad, as a pure function. If it wins the open
 * lot, the cheapest set of remaining players (at base price) that fills each unmet role minimum and
 * then the squad minimum, without passing the squad maximum or a restricted category's cap. The
 * open lot's own role and tags count as already won. The search is exact: it picks how many players
 * to take from each (role, restricted categories) group, cheapest first within a group.
 *
 * <p>When the remaining pool cannot fill the squad at all, the reserve is what it takes to get as
 * close as the pool allows: a franchise is never stopped from bidding on a player that brings it
 * nearer a legal squad. Only when winning the lot itself leaves the squad further from legal than
 * letting it go (it takes the last place, or a capped place a needed role must use) is the bid
 * {@link Result#blocked() blocked}.
 */
public final class SquadReserve {

  private SquadReserve() {}

  /**
   * The reserve: {@code cost} in the smallest currency unit, {@code role}, the pack role behind
   * most of it (or, when {@code blocked}, the role winning would leave short), or null when only
   * the squad minimum is concerned. {@code blocked} means winning the lot leaves the squad less
   * complete than passing it, at any price.
   */
  public record Result(long cost, String role, boolean blocked) {}

  /**
   * @param held the lots the franchise already holds
   * @param lot the open lot, counted as won
   * @param byPrice every lot of the auction in base-price order; only those after {@code lot} in
   *     play order are still to come
   */
  public static Result of(SquadRules rules, List<Lot> held, Lot lot, List<Lot> byPrice) {
    List<Lot> won = new ArrayList<>(held);
    won.add(lot);
    Fill with = fill(rules, won, byPrice, lot.no());
    if (with.shortfall() > 0) {
      Fill without = fill(rules, held, byPrice, lot.no());
      if (with.shortfall() > without.shortfall()) {
        return new Result(0, worse(with, without), true);
      }
    }
    String heaviest =
        with.byRole().entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    return new Result(with.cost(), heaviest, false);
  }

  /** How many places the pool leaves unfilled, at what cost, and the cost and shortfall by role. */
  private record Fill(
      int shortfall, long cost, Map<String, Long> byRole, Map<String, Integer> shortByRole) {}

  /** The role {@code with} leaves shorter than {@code without}; null if only the total differs. */
  private static String worse(Fill with, Fill without) {
    return with.shortByRole().entrySet().stream()
        .filter(e -> e.getValue() > without.shortByRole().getOrDefault(e.getKey(), 0))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  /**
   * The cheapest fill of {@code squad} from the lots after {@code after}, closest to legal first.
   */
  private static Fill fill(SquadRules rules, List<Lot> squad, List<Lot> byPrice, int after) {
    List<String> roles = new ArrayList<>();
    List<Integer> needs = new ArrayList<>();
    for (var entry : rules.roleMinimums().entrySet()) {
      int have = (int) squad.stream().filter(l -> entry.getKey().equals(l.role())).count();
      if (entry.getValue() > have) {
        roles.add(entry.getKey());
        needs.add(entry.getValue() - have);
      }
    }
    int roleNeed = needs.stream().mapToInt(Integer::intValue).sum();
    int extra = Math.max(0, rules.min() - squad.size() - roleNeed);
    int room = Math.max(0, rules.max() - squad.size());
    List<SquadRules.Quota> quotas = rules.quotas();
    int[] caps = new int[quotas.size()];
    for (int q = 0; q < caps.length; q++) {
      caps[q] = Math.max(0, placesLeft(quotas.get(q), squad));
    }
    int places = roleNeed + extra;
    if (places == 0) {
      return new Fill(0, 0, Map.of(), Map.of());
    }

    // Group the players still to come by role and restricted categories, cheapest first. A group
    // never needs more than its role's need plus the filler places, nor more than the room left.
    Map<List<Integer>, Group> groups = new LinkedHashMap<>();
    for (Lot candidate : byPrice) {
      if (candidate.no() <= after) {
        continue;
      }
      int role = roles.indexOf(candidate.role());
      int mask = 0;
      boolean capped = false;
      for (int q = 0; q < caps.length; q++) {
        if (has(candidate, quotas.get(q).tag())) {
          mask |= 1 << q;
          capped |= caps[q] == 0;
        }
      }
      if (capped) {
        continue;
      }
      int limit = Math.min(room, (role >= 0 ? needs.get(role) : 0) + extra);
      Group group = groups.computeIfAbsent(List.of(role, mask), k -> new Group(role, k.get(1)));
      if (group.prices.size() < limit) {
        group.prices.add(candidate.basePrice());
      }
    }

    int[] start = new int[roles.size() + 2 + caps.length];
    for (int r = 0; r < roles.size(); r++) {
      start[r] = needs.get(r);
    }
    start[roles.size()] = extra;
    start[roles.size() + 1] = room;
    System.arraycopy(caps, 0, start, roles.size() + 2, caps.length);

    Search search = new Search(List.copyOf(groups.values()), roles.size(), caps.length);
    Best best = search.best(0, start);

    // Walk the chosen counts to attribute cost and shortfall to roles (sorted, so ties are stable).
    Map<String, Long> byRole = new TreeMap<>();
    int[] state = start;
    for (int g = 0; g < search.groups.size(); g++) {
      Group group = search.groups.get(g);
      int k = search.best(g, state).take;
      if (group.role >= 0 && k > 0) {
        int toRole = Math.min(k, state[group.role]);
        byRole.merge(roles.get(group.role), group.prefix(toRole), Long::sum);
      }
      state = search.after(group, state, k);
    }
    Map<String, Integer> shortByRole = new TreeMap<>();
    for (int r = 0; r < roles.size(); r++) {
      if (state[r] > 0) {
        shortByRole.put(roles.get(r), state[r]);
      }
    }
    return new Fill(best.shortfall, best.cost, byRole, shortByRole);
  }

  /**
   * Places {@code squad} leaves under {@code quota}'s cap; negative when it already holds too many.
   */
  public static int placesLeft(SquadRules.Quota quota, List<Lot> squad) {
    return quota.max() - (int) squad.stream().filter(l -> has(l, quota.tag())).count();
  }

  private static boolean has(Lot lot, String tag) {
    return lot.tags().contains(tag);
  }

  /** Players of one role (index into the needed roles, -1 for any other) and category mask. */
  private static final class Group {
    final int role;
    final int mask;
    final List<Long> prices = new ArrayList<>();

    Group(int role, int mask) {
      this.role = role;
      this.mask = mask;
    }

    long prefix(int k) {
      long sum = 0;
      for (int i = 0; i < k; i++) {
        sum += prices.get(i);
      }
      return sum;
    }
  }

  /** The best outcome from group {@code g} on: fewest places left unfilled, then lowest cost. */
  private record Best(int shortfall, long cost, int take) {
    boolean beats(Best other) {
      return shortfall != other.shortfall ? shortfall < other.shortfall : cost < other.cost;
    }
  }

  /**
   * Exact search of how many players to take from each group. The state is the need left per role,
   * then filler places, room under the squad maximum, and places left per category cap.
   */
  private static final class Search {
    final List<Group> groups;
    final int roleCount;
    final int capCount;
    final Map<Key, Best> memo = new HashMap<>();

    Search(List<Group> groups, int roleCount, int capCount) {
      this.groups = groups;
      this.roleCount = roleCount;
      this.capCount = capCount;
    }

    Best best(int g, int[] state) {
      if (g == groups.size()) {
        int left = state[roleCount];
        for (int r = 0; r < roleCount; r++) {
          left += state[r];
        }
        return new Best(left, 0, 0);
      }
      Key key = new Key(g, state);
      Best known = memo.get(key);
      if (known != null) {
        return known;
      }
      Group group = groups.get(g);
      int most = Math.min(group.prices.size(), state[roleCount + 1]);
      most = Math.min(most, (group.role >= 0 ? state[group.role] : 0) + state[roleCount]);
      for (int q = 0; q < capCount; q++) {
        if ((group.mask & (1 << q)) != 0) {
          most = Math.min(most, state[roleCount + 2 + q]);
        }
      }
      Best best = null;
      long cost = 0;
      for (int k = 0; k <= most; k++) {
        if (k > 0) {
          cost += group.prices.get(k - 1);
        }
        Best rest = best(g + 1, after(group, state, k));
        Best option = new Best(rest.shortfall, rest.cost + cost, k);
        if (best == null || option.beats(best)) {
          best = option;
        }
      }
      memo.put(key, best);
      return best;
    }

    /**
     * The state after taking {@code k} players from {@code group}: role need first, then filler.
     */
    int[] after(Group group, int[] state, int k) {
      if (k == 0) {
        return state;
      }
      int[] next = state.clone();
      int toRole = group.role >= 0 ? Math.min(k, next[group.role]) : 0;
      if (group.role >= 0) {
        next[group.role] -= toRole;
      }
      next[roleCount] -= k - toRole;
      next[roleCount + 1] -= k;
      for (int q = 0; q < capCount; q++) {
        if ((group.mask & (1 << q)) != 0) {
          next[roleCount + 2 + q] -= k;
        }
      }
      return next;
    }
  }

  private record Key(int group, int[] state) {
    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && group == k.group && Arrays.equals(state, k.state);
    }

    @Override
    public int hashCode() {
      return 31 * group + Arrays.hashCode(state);
    }

    @Override
    public String toString() {
      return group + Arrays.toString(state);
    }
  }
}
