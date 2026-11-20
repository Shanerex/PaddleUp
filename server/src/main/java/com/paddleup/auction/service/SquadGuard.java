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
 * past a restricted category's cap, into a squad the pool can no longer complete, or leaving too
 * little purse to fill the minimums at base price (see {@link SquadReserve}). The rules are the
 * ones frozen at go-live, so a room edit or a restart changes nothing. A state without rules (a log
 * from before this guard) has no squad guard.
 */
@Component
@Order(1)
public class SquadGuard implements BidGuard {

  @Override
  public Optional<BidLock> limit(AuctionState state, UUID franchiseId) {
    SquadRules rules = state.squadRules();
    Lot lot = state.lot();
    if (rules == null || lot == null) {
      return Optional.empty();
    }
    List<Lot> held = held(state, franchiseId);
    if (held.size() >= rules.max()) {
      return Optional.of(BidLock.any(Reason.SQUAD_FULL, null, null, null));
    }
    for (SquadRules.Quota quota : rules.quotas()) {
      if (lot.tags().contains(quota.tag()) && SquadReserve.placesLeft(quota, held) <= 0) {
        return Optional.of(BidLock.any(Reason.QUOTA, null, quota.category(), quota.max()));
      }
    }
    SquadReserve.Result reserve =
        SquadReserve.of(rules, held, lot, state.lotsByPrice(), state.queue());
    if (reserve.blocked()) {
      return Optional.of(BidLock.any(Reason.NO_ROOM, reserve.role(), null, null));
    }
    if (reserve.cost() == 0) {
      return Optional.empty();
    }
    long purse = state.purses().getOrDefault(franchiseId, 0L);
    return Optional.of(
        new BidLock(Reason.RESERVE, reserve.role(), null, null, purse - reserve.cost()));
  }

  private static List<Lot> held(AuctionState state, UUID franchiseId) {
    return state.squads().getOrDefault(franchiseId, List.of()).stream()
        .map(SquadEntry::lot)
        .map(no -> state.lots().get(no - 1))
        .toList();
  }
}
