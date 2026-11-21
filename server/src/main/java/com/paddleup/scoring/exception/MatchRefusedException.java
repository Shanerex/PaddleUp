package com.paddleup.scoring.exception;

/**
 * A stat entry names something it may not use: an unknown match ({@code unknown_match}, 404), an
 * unknown player ({@code unknown_player}, 404) or a match that has not started ({@code
 * match_not_started}, 409).
 */
public class MatchRefusedException extends RuntimeException {

  private final String code;
  private final boolean conflict;

  private MatchRefusedException(String code, String message, boolean conflict) {
    super(message);
    this.code = code;
    this.conflict = conflict;
  }

  public static MatchRefusedException unknownMatch() {
    return new MatchRefusedException("unknown_match", "This room has no such match", false);
  }

  public static MatchRefusedException unknownPlayer() {
    return new MatchRefusedException("unknown_player", "This room has no such player", false);
  }

  public static MatchRefusedException notStarted() {
    return new MatchRefusedException("match_not_started", "This match has not started yet", true);
  }

  public String code() {
    return code;
  }

  /** True for 409, false for 404. */
  public boolean conflict() {
    return conflict;
  }
}
