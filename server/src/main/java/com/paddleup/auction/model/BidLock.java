package com.paddleup.auction.model;

/**
 * A limit a bid guard puts on a franchise for the open lot: a {@link Reason} code, plus the pack
 * role or restricted category it concerns and that category's frozen {@code cap} when there is one.
 * {@code above} is the highest bid the limit still allows; null means it allows no bid at all. A
 * limit holds for the whole lot, since purses and squads change only when a lot closes.
 */
public record BidLock(String reason, String role, String category, Integer cap, Long above) {

  /** A limit that allows no bid. */
  public static BidLock any(String reason, String role, String category, Integer cap) {
    return new BidLock(reason, role, category, cap, null);
  }

  public boolean refuses(long amount) {
    return above == null || amount > above;
  }
}
