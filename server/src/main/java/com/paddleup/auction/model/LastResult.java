package com.paddleup.auction.model;

import java.util.UUID;

/** How the most recent lot closed. {@code franchiseId} is null when it went unsold. */
public record LastResult(int lot, UUID franchiseId, long amount) {

  public boolean sold() {
    return franchiseId != null;
  }
}
