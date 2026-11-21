package com.paddleup.scoring.service;

import com.paddleup.scoring.model.MatchSummary;
import java.time.Instant;

/** The source chip of a match, first match wins. Pure. */
final class MatchChips {

  static final String UPCOMING = "UPCOMING";
  static final String NO_DATA = "NO_DATA";
  static final String MANUAL = "MANUAL";
  static final String PROVIDER_MANUAL = "PROVIDER_MANUAL";
  static final String PROVIDER = "PROVIDER";

  private MatchChips() {}

  static String of(MatchSummary match, Instant now) {
    if (match.startsAt().isAfter(now)) {
      return UPCOMING;
    }
    if (match.providerRows() == 0) {
      return match.overridePlayers() == 0 ? NO_DATA : MANUAL;
    }
    return match.overridePlayers() > 0 ? PROVIDER_MANUAL : PROVIDER;
  }
}
