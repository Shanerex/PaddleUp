package com.paddleup.rooms.model;

import java.util.Arrays;
import java.util.Locale;

/** What a room log row records. Stored as the lower-case name. */
public enum LogKind {
  ROOM_CREATED,
  CONFIG_SAVED,
  INVITE_ISSUED,
  INVITE_REVOKED,
  INVITE_REGENERATED,
  MANAGER_JOINED,
  MANAGER_REMOVED,
  COMMISSIONER_HANDOVER,
  TRANSITION,
  POOL_COPIED,
  POOL_PLAYER_ADDED,
  POOL_PLAYER_EDITED,
  POOL_PLAYER_REMOVED,
  POOL_RESET,
  SET_ORDER_SAVED;

  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static LogKind fromValue(String value) {
    return Arrays.stream(values())
        .filter(k -> k.value().equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown log kind " + value));
  }
}
