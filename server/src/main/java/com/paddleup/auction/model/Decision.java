package com.paddleup.auction.model;

import java.util.List;

/** What the rules decide for an intent: accept it as events, reject it, or refuse the phase. */
public sealed interface Decision {

  /** The intent holds; persist these events. */
  record Accepted(List<AuctionEvent> events) implements Decision {}

  /** A rule said no. Nothing is written. */
  record Rejected(String reason) implements Decision {}

  /** The auction is not taking this intent now (one of the 409 codes). */
  record PhaseError(String code) implements Decision {}
}
