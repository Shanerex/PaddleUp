package com.paddleup.scoring.exception;

/** The request is fine but the rule set's state refuses it; maps to 409 with a code. */
public class RuleSetConflictException extends RuntimeException {

  /** The first match has started: use the post-freeze change path instead. */
  public static final String FROZEN = "frozen";

  /** The set changed since the caller read it. */
  public static final String STALE_VERSION = "stale_version";

  /** A post-freeze change was sent before the freeze; use the ordinary edit. */
  public static final String NOT_FROZEN = "not_frozen";

  private final String code;

  public RuleSetConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
