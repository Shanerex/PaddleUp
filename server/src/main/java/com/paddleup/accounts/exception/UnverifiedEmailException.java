package com.paddleup.accounts.exception;

/** Google reported the email as unverified, so it must not create or link an account. */
public class UnverifiedEmailException extends RuntimeException {

  public UnverifiedEmailException() {
    super("Google email is not verified");
  }
}
