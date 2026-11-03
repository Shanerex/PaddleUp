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
  TRANSITION;

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
