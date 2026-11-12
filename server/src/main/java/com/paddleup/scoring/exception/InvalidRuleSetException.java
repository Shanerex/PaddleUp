package com.paddleup.scoring.exception;

import com.paddleup.rooms.dto.Violation;
import java.util.List;

/** A rule set or a change request broke one or more rules; maps to 422 listing every one. */
public class InvalidRuleSetException extends RuntimeException {

  private final transient List<Violation> violations;

  public InvalidRuleSetException(List<Violation> violations) {
    super("The request has " + violations.size() + " problem(s)");
    this.violations = List.copyOf(violations);
  }

  public List<Violation> violations() {
    return violations;
  }
}
