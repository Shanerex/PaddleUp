package com.paddleup.rooms.util;

import java.security.SecureRandom;
import java.util.Locale;

/** Invite codes: Crockford base32, five random bits per character, from {@link SecureRandom}. */
public final class InviteCodes {

  /** Crockford base32: digits and letters without I, L, O and U. */
  static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

  private static final SecureRandom RANDOM = new SecureRandom();

  private InviteCodes() {}

  /** A new code of {@code length} characters, which is {@code 5 * length} bits. */
  public static String generate(int length) {
    var code = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }

  /**
   * What a person typed, turned into the stored form: upper case, spaces and dashes dropped, and
   * the look-alikes O, I and L read as 0, 1 and 1. Returns null when anything else is left that
   * cannot be in a code.
   */
  public static String normalise(String raw) {
    if (raw == null || raw.length() > 64) {
      return null;
    }
    var out = new StringBuilder();
    for (char c : raw.toUpperCase(Locale.ROOT).toCharArray()) {
      if (c == ' ' || c == '-') {
        continue;
      }
      char mapped =
          switch (c) {
            case 'O' -> '0';
            case 'I', 'L' -> '1';
            default -> c;
          };
      if (ALPHABET.indexOf(mapped) < 0) {
        return null;
      }
      out.append(mapped);
    }
    return out.toString();
  }
}
