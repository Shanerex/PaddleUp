package com.paddleup.packs.service;

import com.paddleup.accounts.service.DisplayNameSanitiser;
import com.paddleup.packs.dto.PresetDto;

/**
 * Limits for the optional display fields of a pack. A pack is checked at load and a room config at
 * save, with the same rules, so a value one accepts the other accepts too.
 */
public final class PackDisplayRules {

  public static final int ROLE_SHORT_MAX = 6;
  public static final int STAT_GROUP_MAX = 24;
  public static final int UNIT_LABEL_MAX = 20;
  public static final int UNIT_SHORT_MAX = 6;

  private PackDisplayRules() {}

  /** True when {@code text} is non-blank plain text of at most {@code max} characters. */
  public static boolean plainText(String text, int max) {
    return text != null
        && !text.isBlank()
        && text.codePointCount(0, text.length()) <= max
        && text.equals(DisplayNameSanitiser.sanitise(text));
  }

  /**
   * What is wrong with the short key and minor unit of {@code unit}, or null when they are fine
   * (absent is fine). The major label and divisor are checked by the caller.
   */
  public static String unitProblem(PresetDto.DisplayUnit unit) {
    if (unit.abbr() != null && !plainText(unit.abbr(), UNIT_SHORT_MAX)) {
      return "Short must be plain text of at most " + UNIT_SHORT_MAX + " characters";
    }
    var minor = unit.minor();
    if (minor == null) {
      return null;
    }
    if (!plainText(minor.label(), UNIT_LABEL_MAX)) {
      return "Minor label must be plain text of at most " + UNIT_LABEL_MAX + " characters";
    }
    if (!plainText(minor.abbr(), UNIT_SHORT_MAX)) {
      return "Minor short must be plain text of at most " + UNIT_SHORT_MAX + " characters";
    }
    if (minor.divisor() <= 0 || minor.divisor() >= unit.divisor()) {
      return "Minor divisor must be above 0 and below the divisor";
    }
    return null;
  }
}
