package com.paddleup.rooms.exception;

/** The pool has no such player; maps to 404. */
public class PoolPlayerNotFoundException extends RuntimeException {

  public PoolPlayerNotFoundException() {
    super("Player not found in this pool");
  }
}
