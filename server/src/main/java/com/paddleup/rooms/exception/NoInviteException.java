package com.paddleup.rooms.exception;

/** The room has no active invite right now. */
public class NoInviteException extends RuntimeException {

  public NoInviteException() {
    super("No active invite");
  }
}
