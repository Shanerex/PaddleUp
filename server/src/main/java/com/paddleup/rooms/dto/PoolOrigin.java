package com.paddleup.rooms.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** Where a pool player came from: untouched from the dataset, edited since, or added by hand. */
public enum PoolOrigin {
  SOURCE,
  EDITED,
  ADDED;

  @JsonValue
  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  @JsonCreator
  public static PoolOrigin fromValue(String value) {
    return Arrays.stream(values())
        .filter(o -> o.value().equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown pool origin " + value));
  }
}
