package com.paddleup.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A UTC clock that follows real time until a test freezes or moves it. */
public class MutableClock extends Clock {

  private final AtomicReference<Instant> frozen = new AtomicReference<>();

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    Instant fixed = frozen.get();
    return fixed != null ? fixed : Instant.now();
  }

  /** Stops the clock at {@code at}. */
  public void freeze(Instant at) {
    frozen.set(at);
  }

  /** Moves the clock forward from its present reading and holds it there. */
  public void advance(Duration by) {
    frozen.set(instant().plus(by));
  }

  /** Follows real time again. */
  public void reset() {
    frozen.set(null);
  }
}
