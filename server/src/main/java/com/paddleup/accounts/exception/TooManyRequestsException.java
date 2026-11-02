package com.paddleup.accounts.exception;

import java.time.Duration;

/** A sign-in rate limit was hit; the caller may retry after {@link #retryAfter()}. */
public class TooManyRequestsException extends RuntimeException {

  private final Duration retryAfter;

  public TooManyRequestsException(Duration retryAfter) {
    super("Too many sign-in attempts");
    this.retryAfter =
        retryAfter.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
