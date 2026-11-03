package com.paddleup.rooms.exception;

import java.time.Duration;

/** Too many failed invite-code attempts; the caller may retry after {@link #retryAfter()}. */
public class TooManyJoinAttemptsException extends RuntimeException {

  private final Duration retryAfter;

  public TooManyJoinAttemptsException(Duration retryAfter) {
    super("Too many invite attempts");
    this.retryAfter =
        retryAfter.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
