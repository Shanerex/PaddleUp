package com.paddleup.scoring.model;

import java.util.Arrays;
import java.util.Locale;

/** What a rule change log row records. Stored as the lower-case name. */
public enum RuleLogKind {
  EDIT,
  REVERT,
  POST_FREEZE_CHANGE;

  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static RuleLogKind fromValue(String value) {
    return Arrays.stream(values())
        .filter(k -> k.value().equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown rule log kind " + value));
  }
}
