package com.paddleup.auction.util;

import java.text.Normalizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

/**
 * Cleans the free-text reason a Commissioner gives for re-opening a lot, so it is safe to store, to
 * show in the audit log and to put in a prompt. Stateless; the same rules as the scoring module's
 * reason text, which is private to that module.
 */
public final class ReasonText {

  public static final int MAX_LENGTH = 500;

  /** Input larger than this is refused before parsing. */
  private static final int MAX_INPUT = 4000;

  private ReasonText() {}

  /**
   * The reason without markup, control characters or repeated spaces; empty when it is missing or
   * far too long to be a reason. The caller checks the length of the result.
   */
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
}
