package com.paddleup.rooms.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Where a room is in its life. States only move forward, in this order. */
public enum RoomState {
  SETUP,
  RETENTION,
  AUCTION_SCHEDULED,
  AUCTION_LIVE,
  SEASON_LIVE,
  COMPLETED;

  /** The wire and database form, such as {@code auction_live}. */
  @JsonValue
  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  @JsonCreator
  public static RoomState fromValue(String value) {
    return Arrays.stream(values())
        .filter(s -> s.value().equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown room state"));
  }
}
