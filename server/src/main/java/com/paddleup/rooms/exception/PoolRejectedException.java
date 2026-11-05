package com.paddleup.rooms.exception;

import com.paddleup.rooms.dto.Violation;
import java.util.List;

/** A pool write broke a row or set rule; maps to 422 listing each field. Nothing was changed. */
public class PoolRejectedException extends RuntimeException {

  private final transient List<Violation> violations;

  public PoolRejectedException(List<Violation> violations) {
    super("The pool change has " + violations.size() + " problem(s)");
    this.violations = List.copyOf(violations);
  }

  public List<Violation> violations() {
    return violations;
  }
}
