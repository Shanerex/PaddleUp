package com.paddleup.auction.model;

import java.util.Locale;

/**
 * Where an auction is. {@code NONE} means nothing has been scheduled for the room; {@code
 * SCHEDULED} is the lobby. Only {@code LIVE} takes bids and passes.
 */
public enum Phase {
  NONE,
  SCHEDULED,
  LIVE,
  PAUSED,
  ADJOURNED,
  RESUME_LOBBY,
  MAIN_DONE,
  NOMINATING,
  ENDED;

  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Phase fromValue(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
