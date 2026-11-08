package com.paddleup.auction.model;

import java.time.Instant;

/**
 * The answer to an intent. {@code seq}, {@code amount} and {@code deadline} describe the accepted
 * event; a rejection carries only its {@code reason}.
 */
public record Outcome(
    boolean accepted, String reason, Long seq, int lot, Long amount, Instant deadline) {

  public static Outcome rejected(int lot, String reason) {
    return new Outcome(false, reason, null, lot, null, null);
  }

  public static Outcome accepted(int lot, long seq, Long amount, Instant deadline) {
    return new Outcome(true, null, seq, lot, amount, deadline);
  }
}
