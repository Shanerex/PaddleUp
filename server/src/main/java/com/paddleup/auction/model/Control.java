package com.paddleup.auction.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * What the Commissioner asks the auction to do. {@code lot} is 0 when the action names none; {@code
 * reason} is used by re-open and {@code resumeAt} by adjourn and change time. Whether the caller is
 * the Commissioner is checked before this reaches the runner.
 */
public record Control(
    Action action, UUID intentId, UUID actor, int lot, String reason, Instant resumeAt) {

  public enum Action {
    PAUSE,
    RESUME,
    SKIP,
    REOPEN,
    ADJOURN,
    CHANGE_TIME,
    RESUME_NOW,
    START_NOW,
    OPEN_ROUND,
    END_AUCTION,
    END_ROUND;

    /** The wire form, such as {@code resume_now}. */
    public String value() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Action> fromValue(String value) {
      return Arrays.stream(values()).filter(a -> a.value().equals(value)).findFirst();
    }
  }
}
