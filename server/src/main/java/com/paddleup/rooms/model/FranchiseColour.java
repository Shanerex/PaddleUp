package com.paddleup.rooms.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The 12 franchise hues from the design system; each is used by one franchise per room. */
public enum FranchiseColour {
  CRIMSON(15),
  TANGERINE(45),
  BRONZE(75),
  OLIVE(105),
  FERN(135),
  EMERALD(165),
  TEAL(195),
  SKY(225),
  COBALT(255),
  IRIS(285),
  ORCHID(315),
  ROSE(345);

  private final int hue;

  FranchiseColour(int hue) {
    this.hue = hue;
  }

  public int hue() {
    return hue;
  }

  /** The stored and wire form, such as {@code crimson}. */
  public String key() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Optional<FranchiseColour> fromKey(String key) {
    return Arrays.stream(values()).filter(c -> c.key().equals(key)).findFirst();
  }

  public static List<String> keys() {
    return Arrays.stream(values()).map(FranchiseColour::key).toList();
  }
}
