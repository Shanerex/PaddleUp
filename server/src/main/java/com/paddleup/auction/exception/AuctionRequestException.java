package com.paddleup.auction.exception;

/** The request itself is wrong; maps to 400 and names the field. */
public class AuctionRequestException extends RuntimeException {

  private final String field;

  public AuctionRequestException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
