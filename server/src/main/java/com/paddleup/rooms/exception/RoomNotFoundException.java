package com.paddleup.rooms.exception;

/** The room does not exist, or the caller is not a member; both answer 404 so ids stay private. */
public class RoomNotFoundException extends RuntimeException {

  public RoomNotFoundException() {
    super("Room not found");
  }
}
