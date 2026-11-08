package com.paddleup.auction.exception;

/** The auction did not answer in time; maps to 503. The intent may still be applied. */
public class AuctionBusyException extends RuntimeException {

  public AuctionBusyException(Throwable cause) {
    super("The auction is busy; check its state and try again", cause);
  }
}
