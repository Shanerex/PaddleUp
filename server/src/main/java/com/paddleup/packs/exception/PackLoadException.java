package com.paddleup.packs.exception;

import java.util.List;
import java.util.stream.Collectors;

/** Aborts startup. Holds every violation found across all pack files, one line each. */
public class PackLoadException extends RuntimeException {

  private final List<PackViolation> violations;

  public PackLoadException(List<PackViolation> violations) {
    super(message(violations));
    this.violations = List.copyOf(violations);
  }

  public List<PackViolation> violations() {
    return violations;
  }

  private static String message(List<PackViolation> violations) {
    return "Sport packs are invalid ("
        + violations.size()
        + " violation"
        + (violations.size() == 1 ? "" : "s")
        + "):\n"
        + violations.stream().map(v -> "  - " + v).collect(Collectors.joining("\n"));
  }
}
