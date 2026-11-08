package com.paddleup.auction.service;

import com.paddleup.auction.model.AuctionState;
import java.util.Optional;
import java.util.UUID;

/**
 * A rule a bid must pass beyond the ladder and the leader checks. Every guard bean is asked in
 * order and the first reason wins. Purse ships here; the squad-composition guard (FR-44) plugs in
 * as another bean in the eligibility spec, with no change to the intent rules.
 */
public interface BidGuard {

  /**
   * The reason {@code franchiseId} may not bid {@code amount} on the open lot now, or empty if this
   * guard has no objection.
   */
  Optional<String> check(AuctionState state, UUID franchiseId, long amount);
}
