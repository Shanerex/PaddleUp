package com.paddleup.auction.model;

/** The codes an intent can be refused with. Rule rejections write no event. */
public final class Reason {

  private Reason() {}

  // A rule rejected the intent (answered 200, outcome rejected).
  public static final String NOT_A_FRANCHISE = "not_a_franchise";
  public static final String LEADER = "leader";
  public static final String PASSED = "passed";
  public static final String PURSE = "purse";
  public static final String SQUAD_FULL = "squad_full";
  public static final String QUOTA = "quota";
  public static final String RESERVE = "reserve";
  public static final String NO_ROOM = "no_room";
  public static final String OUTBID = "outbid";
  public static final String LOT_CLOSED = "lot_closed";

  // A control or pick was refused (answered 200, outcome rejected, nothing written).
  public static final String WRONG_PHASE = "wrong_phase";
  public static final String HAS_BID = "has_bid";
  public static final String REASON_REQUIRED = "reason_required";
  public static final String REASON_TOO_LONG = "reason_too_long";
  public static final String NOT_CLOSED = "not_closed";
  public static final String SUPERSEDED = "superseded";
  public static final String TIME_INVALID = "time_invalid";
  public static final String ROUND_DONE = "round_done";
  public static final String NOT_UNSOLD = "not_unsold";
  public static final String OVER_SLOTS = "over_slots";

  // The auction is not in a phase that takes the intent (answered 409).
  public static final String NOT_LIVE = "not_live";
  public static final String NO_OPEN_LOT = "no_open_lot";
  public static final String ENDED = "ended";
  public static final String PAUSED = "paused";
  public static final String ADJOURNED = "adjourned";
  public static final String RESUME_LOBBY = "resume_lobby";
  public static final String MAIN_DONE = "main_done";
  public static final String NOMINATING = "nominating";

  // The lobby ready toggle is refused (answered 409).
  public static final String NOT_SCHEDULED = "not_scheduled";
}
