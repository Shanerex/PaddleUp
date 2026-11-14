package com.paddleup.auction.service;

import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.BidLock;
import java.util.Optional;
import java.util.UUID;

/**
 * A rule a bid must pass beyond the ladder and the leader checks. Every guard bean is asked in
 * order and the first refusal wins. A guard states its rule as a {@link BidLock limit} for the open
 * lot, so the same answer decides a bid and tells the viewer, before they press, which amounts they
 * cannot bid.
 */
public interface BidGuard {

  /**
   * The limit this guard puts on {@code franchiseId} for the open lot, or empty if it has none. It
   * must not depend on the current bid: it holds until the lot closes.
   */
  Optional<BidLock> limit(AuctionState state, UUID franchiseId);

  /**
   * The reason {@code franchiseId} may not bid {@code amount} on the open lot now, or empty if this
   * guard has no objection.
   */
  default Optional<String> check(AuctionState state, UUID franchiseId, long amount) {
    return limit(state, franchiseId).filter(l -> l.refuses(amount)).map(BidLock::reason);
  }
}
