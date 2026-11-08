package com.paddleup.rooms.exception;

/** The request is fine but the room's current state refuses it; maps to 409 with a code. */
public class RoomConflictException extends RuntimeException {

  public static final String ILLEGAL_STATE = "illegal_state";
  public static final String STALE_VERSION = "stale_version";
  public static final String ROOM_FULL = "room_full";
  public static final String ROOM_LOCKED = "room_locked";
  public static final String DUPLICATE = "duplicate";
  public static final String PACK_MISMATCH = "pack_mismatch";
  public static final String PRICE_MISMATCH = "price_mismatch";

  private final String code;

  public RoomConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
