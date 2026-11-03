package com.paddleup.rooms.exception;

/** A member asked for something only the Commissioner, or the franchise owner, may do. */
public class RoomForbiddenException extends RuntimeException {

  public RoomForbiddenException(String message) {
    super(message);
  }
}
