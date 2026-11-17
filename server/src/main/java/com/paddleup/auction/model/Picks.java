package com.paddleup.auction.model;

import java.util.List;
import java.util.UUID;

/**
 * A franchise's accelerated-round nominations, as unsold lot numbers. Replaces its earlier ones.
 */
public record Picks(UUID intentId, UUID actor, UUID franchiseId, List<Integer> lots) {

  public Picks {
    lots = List.copyOf(lots);
  }
}
