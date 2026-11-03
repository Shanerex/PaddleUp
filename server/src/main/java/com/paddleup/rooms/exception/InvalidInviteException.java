package com.paddleup.rooms.exception;

/** The code is unknown, revoked or expired. One error for all three, so none can be told apart. */
public class InvalidInviteException extends RuntimeException {

  public InvalidInviteException() {
    super("This invite is not valid");
  }
}
