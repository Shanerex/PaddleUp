package com.paddleup.auction.model;

/** The codes an intent can be refused with. Rule rejections write no event. */
public final class Reason {

  private Reason() {}

  // A rule rejected the intent (answered 200, outcome rejected).
  public static final String NOT_A_FRANCHISE = "not_a_franchise";
  public static final String LEADER = "leader";
  public static final String PASSED = "passed";
  public static final String PURSE = "purse";
  public static final String OUTBID = "outbid";
  public static final String LOT_CLOSED = "lot_closed";

  // The auction is not in a phase that takes the intent (answered 409).
  public static final String NOT_LIVE = "not_live";
  public static final String NO_OPEN_LOT = "no_open_lot";
  public static final String ENDED = "ended";
}
