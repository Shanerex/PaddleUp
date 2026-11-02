package com.paddleup.accounts.exception;

/** A protected operation was reached without a signed-in account. */
public class NotSignedInException extends RuntimeException {

  public NotSignedInException() {
    super("Not signed in");
  }
}
