package com.paddleup.rooms.service;

import com.paddleup.accounts.service.DisplayNameSanitiser;
import com.paddleup.rooms.exception.RoomsRequestException;
import com.paddleup.rooms.model.FranchiseColour;
import java.util.Locale;

/** What a franchise name, colour and monogram must look like. Uniqueness is checked elsewhere. */
final class FranchiseRules {

  static final int MONOGRAM_MAX = 3;

  /** Cleaned, valid franchise fields. */
  record Clean(String name, String colour, String monogram) {}

  private FranchiseRules() {}

  /** The cleaned fields, or a {@link RoomsRequestException} naming the first bad one. */
  static Clean clean(String name, String colour, String monogram) {
    return new Clean(cleanName(name), cleanColour(colour), cleanMonogram(monogram));
  }

  static String cleanName(String raw) {
    String name = DisplayNameSanitiser.sanitise(raw);
    int length = name.codePointCount(0, name.length());
    if (length < DisplayNameSanitiser.MIN_LENGTH || length > DisplayNameSanitiser.MAX_LENGTH) {
      throw new RoomsRequestException(
          "name",
          "Name must be "
              + DisplayNameSanitiser.MIN_LENGTH
              + " to "
              + DisplayNameSanitiser.MAX_LENGTH
              + " characters");
    }
    return name;
  }

  static String cleanColour(String raw) {
    String key = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    return FranchiseColour.fromKey(key)
        .orElseThrow(
            () -> new RoomsRequestException("colour", "Choose one of the franchise colours"))
        .key();
  }

  static String cleanMonogram(String raw) {
    String monogram = DisplayNameSanitiser.sanitise(raw);
    int length = monogram.codePointCount(0, monogram.length());
    if (length < 1 || length > MONOGRAM_MAX || monogram.chars().anyMatch(Character::isWhitespace)) {
      throw new RoomsRequestException(
          "monogram", "Monogram must be 1 to " + MONOGRAM_MAX + " characters, without spaces");
    }
    return monogram;
  }

  /** The form in which two names count as the same: case-insensitive. */
  static String nameKey(String name) {
    return name.toLowerCase(Locale.ROOT);
  }
}
