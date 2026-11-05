package com.paddleup.packs.exception;

/** A season dataset file that cannot be read at all; maps to 400. Row problems are not this. */
public class DatasetFileException extends RuntimeException {

  /** Why the file was refused. Stored as the lower-case name in the response. */
  public enum Reason {
    TOO_LARGE,
    TOO_MANY_ROWS,
    MALFORMED,
    EMPTY,
    UNSUPPORTED_FORMAT
  }

  private final Reason reason;

  public DatasetFileException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
