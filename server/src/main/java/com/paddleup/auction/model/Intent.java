package com.paddleup.auction.model;

import java.util.UUID;

/**
 * What a franchise asks for. {@code franchiseId} is null when the caller owns no franchise; {@code
 * amount} is used by bids only.
 */
public record Intent(Kind kind, UUID intentId, UUID actor, UUID franchiseId, int lot, long amount) {

  public enum Kind {
    BID,
    PASS
  }

  public static Intent bid(UUID intentId, UUID actor, UUID franchiseId, int lot, long amount) {
    return new Intent(Kind.BID, intentId, actor, franchiseId, lot, amount);
  }

  public static Intent pass(UUID intentId, UUID actor, UUID franchiseId, int lot) {
    return new Intent(Kind.PASS, intentId, actor, franchiseId, lot, 0);
  }
}
