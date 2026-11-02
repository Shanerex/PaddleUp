package com.paddleup.accounts.service;

import com.paddleup.accounts.exception.InvalidRequestException;
import java.text.Normalizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

/** Cleans user-entered display names: no markup, no control characters, 2 to 30 characters. */
public final class DisplayNameSanitiser {

  public static final int MIN_LENGTH = 2;
  public static final int MAX_LENGTH = 30;

  /** Input larger than this is refused before parsing. */
  private static final int MAX_INPUT = 200;

  private DisplayNameSanitiser() {}

  /** Returns the sanitised name, or throws when it is missing or outside 2 to 30 characters. */
  public static String requireValid(String raw) {
    if (raw == null || raw.length() > MAX_INPUT) {
      throw new InvalidRequestException(
          "Display name must be " + MIN_LENGTH + " to " + MAX_LENGTH + " characters");
    }
    String clean = sanitise(raw);
    int length = clean.codePointCount(0, clean.length());
    if (length < MIN_LENGTH || length > MAX_LENGTH) {
      throw new InvalidRequestException(
          "Display name must be " + MIN_LENGTH + " to " + MAX_LENGTH + " characters");
    }
    return clean;
  }

  /** Cleans without a length check; the result may be empty or longer than the limit. */
  public static String sanitise(String raw) {
    if (raw == null || raw.length() > MAX_INPUT) {
      return "";
    }
    String text =
        Jsoup.clean(raw, "", Safelist.none(), new Document.OutputSettings().prettyPrint(false));
    text = Parser.unescapeEntities(text, false);
    text = text.replaceAll("[\\s\\p{Zs}\\p{Zl}\\p{Zp}]+", " ");
    text = text.replaceAll("[\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}<>]", "");
    return Normalizer.normalize(text, Normalizer.Form.NFC).replaceAll(" {2,}", " ").trim();
  }

  /** A usable first name from untrusted input, cut to the limit; empty if nothing is usable. */
  static String sanitiseAndCut(String raw) {
    String clean = sanitise(raw);
    if (clean.codePointCount(0, clean.length()) > MAX_LENGTH) {
      clean = clean.substring(0, clean.offsetByCodePoints(0, MAX_LENGTH)).trim();
    }
    return clean;
  }
}
