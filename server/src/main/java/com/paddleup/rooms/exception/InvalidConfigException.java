package com.paddleup.rooms.exception;

import com.paddleup.rooms.dto.Violation;
import java.util.List;

/** A config broke one or more rules; maps to 400 listing every violation. */
public class InvalidConfigException extends RuntimeException {

  private final transient List<Violation> violations;

  public InvalidConfigException(List<Violation> violations) {
    super("Config has " + violations.size() + " problem(s)");
    this.violations = List.copyOf(violations);
  }

  public List<Violation> violations() {
    return violations;
  }
}
