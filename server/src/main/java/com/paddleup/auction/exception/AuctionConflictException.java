package com.paddleup.auction.exception;

/** The auction is not in a state that takes this request; maps to 409 with a code. */
public class AuctionConflictException extends RuntimeException {

  /** The room cannot be scheduled yet (no pool copy, a small pool, too few franchises). */
  public static final String NOT_READY = "not_ready";

  /** The auction is live or finished and can no longer be scheduled. */
  public static final String LOCKED = "locked";

  private final String code;

  public AuctionConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
