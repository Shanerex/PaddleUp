package com.paddleup.auction.model;

import java.util.Arrays;
import java.util.Locale;

/**
 * Every kind of change an auction can log. The lifeline kinds (FR-48) are reserved for the advisor
 * spec: they carry type, lot and time only, and nothing here writes them yet.
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
  AUCTION_ENDED,
  START_NOW,
  PAUSED,
  RESUMED,
  LOT_SKIPPED,
  LOT_REOPENED,
  ADJOURNED,
  RESUME_TIME_CHANGED,
  RESUME_LOBBY_OPENED,
  MAIN_DONE,
  NOMINATING_OPENED,
  PICKS_SUBMITTED,
  ROUND_STARTED,
  LIFELINE_SPENT,
  LIFELINE_REFUNDED;

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
