package com.paddleup.rooms.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Whether a room can load the newest season data for its pack, and if not, why. */
public enum DatasetFit {
  /** No season data exists for the pack. */
  NONE,
  /** Roles, tags and base prices all fit the room. */
  FITS,
  /** The data uses a role or tag the room's pack does not have. */
  PACK,
  /** The data has a base price the room's purse or increment ladder does not allow. */
  PRICE;

  @JsonValue
  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  @JsonCreator
  public static DatasetFit fromValue(String value) {
    return Arrays.stream(values())
        .filter(f -> f.value().equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown dataset fit " + value));
  }
}
