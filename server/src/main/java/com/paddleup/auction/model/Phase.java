package com.paddleup.auction.model;

import java.util.Locale;

/** Where an auction is. {@code NONE} means nothing has been scheduled for the room. */
public enum Phase {
  NONE,
  SCHEDULED,
  LIVE,
  ENDED;

  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Phase fromValue(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
