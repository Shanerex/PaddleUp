package com.paddleup.auction.util;

import com.paddleup.auction.model.Step;
import java.util.List;

/**
 * The increment ladder as a pure function. The first bid on a lot is its base price; every later
 * bid is the current bid plus the step of the band that holds it, the band with the largest {@code
 * from} not above the current bid.
 */
public final class Ladder {

  private Ladder() {}

  /** The only amount accepted when nobody has bid yet. */
  public static long first(long basePrice) {
    return basePrice;
  }

  /** The only amount accepted after a bid of {@code current}. */
  public static long next(List<Step> ladder, long current) {
    Step band = null;
    for (Step s : ladder) {
      if (s.from() <= current && (band == null || s.from() > band.from())) {
        band = s;
      }
    }
    if (band == null) {
      throw new IllegalArgumentException("No ladder band holds " + current);
    }
    return Math.addExact(current, band.step());
  }

  /** The amount a bid must have: base price if there is no bid yet, else the next step. */
  public static long required(List<Step> ladder, long basePrice, long currentBid) {
    return currentBid <= 0 ? first(basePrice) : next(ladder, currentBid);
  }
}
