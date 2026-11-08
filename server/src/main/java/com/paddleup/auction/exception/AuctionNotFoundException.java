package com.paddleup.auction.exception;

/** The room has no auction scheduled yet; maps to 404. */
public class AuctionNotFoundException extends RuntimeException {

  public AuctionNotFoundException() {
    super("No auction is scheduled for this room");
  }
}
