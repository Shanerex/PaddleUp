package com.paddleup.auction.exception;

/**
 * The event could not be stored, so nothing was applied or broadcast; maps to 503. The caller may
 * send the intent again.
 */
public class LogWriteException extends RuntimeException {

  public LogWriteException(Throwable cause) {
    super("The auction log could not be written", cause);
  }

  public LogWriteException(String message) {
    super(message);
  }
}
