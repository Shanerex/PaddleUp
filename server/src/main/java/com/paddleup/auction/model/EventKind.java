package com.paddleup.auction.model;

import java.util.Arrays;
import java.util.Locale;

/**
 * Every kind of change an auction can log. Lifeline kinds (FR-48) are reserved for a later spec and
 * will carry type, lot and time only.
 */
public enum EventKind {
  SCHEDULED,
  RESCHEDULED,
  START_REFUSED,
  AUCTION_STARTED,
  LOT_OPENED,
  BID_ACCEPTED,
  PASSED,
  LOT_SOLD,
  LOT_UNSOLD,
  LOT_RESUMED,
  AUCTION_ENDED;

  /** The wire and database form, such as {@code bid_accepted}. */
  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static EventKind fromValue(String value) {
    return Arrays.stream(values())
        .filter(k -> k.value().equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown event kind: " + value));
  }
}
