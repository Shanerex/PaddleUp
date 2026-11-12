package com.paddleup.auction.service;

import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.BidLock;
import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.Reason;
import com.paddleup.auction.model.SquadEntry;
import com.paddleup.auction.model.SquadRules;
import com.paddleup.auction.util.SquadReserve;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A franchise may not bid itself into a squad it cannot finish legally: past the squad maximum,
 * past a restricted category's cap, or leaving too little purse to fill the minimums at base price
 * (see {@link SquadReserve}). The rules are the ones frozen at go-live, so a room edit or a restart
 * changes nothing. A state without rules (a log from before this guard) has no squad guard.
 */
@Component
@Order(1)
public class SquadGuard implements BidGuard {

  @Override
  public Optional<String> check(AuctionState state, UUID franchiseId, long amount) {
    return lock(state, franchiseId, amount).map(BidLock::reason);
  }

  /**
   * Why {@code franchiseId} cannot bid {@code amount} on the open lot, with the role or category it
   * concerns, or empty when the squad rules have no objection.
   */
  public Optional<BidLock> lock(AuctionState state, UUID franchiseId, long amount) {
    SquadRules rules = state.squadRules();
    Lot lot = state.lot();
    if (rules == null || lot == null) {
      return Optional.empty();
    }
    List<Lot> held = held(state, franchiseId);
    if (held.size() >= rules.max()) {
      return Optional.of(new BidLock(Reason.SQUAD_FULL, null, null));
    }
    for (SquadRules.Quota quota : rules.quotas()) {
      if (lot.tags().contains(quota.tag()) && count(held, quota.tag()) >= quota.max()) {
        return Optional.of(new BidLock(Reason.QUOTA, null, quota.category()));
      }
    }
    return reserve(state, rules, franchiseId, amount, lot, held);
  }

  /** The bid must leave at least the reserve: what it takes to finish a legal squad at base. */
  private static Optional<BidLock> reserve(
      AuctionState state,
      SquadRules rules,
      UUID franchiseId,
      long amount,
      Lot lot,
      List<Lot> held) {
    List<Lot> remaining = state.lots().subList(state.currentLot(), state.lots().size());
    SquadReserve.Result reserve = SquadReserve.of(rules, held, lot, remaining);
    long left = state.purses().getOrDefault(franchiseId, 0L) - amount;
    return left < reserve.cost()
        ? Optional.of(new BidLock(Reason.RESERVE, reserve.role(), null))
        : Optional.empty();
  }

  private static List<Lot> held(AuctionState state, UUID franchiseId) {
    return state.squads().getOrDefault(franchiseId, List.of()).stream()
        .map(SquadEntry::lot)
        .map(no -> state.lots().get(no - 1))
        .toList();
  }

  private static long count(List<Lot> held, String tag) {
    return held.stream().filter(l -> l.tags().contains(tag)).count();
  }
}
