package com.paddleup.accounts.exception;

/** A sign-in link or code that is wrong, expired, locked or already used. */
public class InvalidLoginException extends RuntimeException {

  public InvalidLoginException() {
    super("Invalid or expired sign-in link or code");
  }
}
