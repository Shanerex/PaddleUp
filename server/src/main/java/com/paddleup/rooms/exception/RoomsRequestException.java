package com.paddleup.rooms.exception;

/** A request field is missing or malformed; maps to 400. */
public class RoomsRequestException extends RuntimeException {

  private final String field;

  public RoomsRequestException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
