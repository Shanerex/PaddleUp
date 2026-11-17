package com.paddleup.auction.service;

import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.BidLock;
import com.paddleup.auction.model.Reason;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** A franchise may not bid more than its remaining purse. */
@Component
@Order(0)
public class PurseGuard implements BidGuard {

  @Override
  public Optional<BidLock> limit(AuctionState state, UUID franchiseId) {
    long purse = state.purses().getOrDefault(franchiseId, 0L);
    return Optional.of(new BidLock(Reason.PURSE, null, null, null, purse));
  }
}
