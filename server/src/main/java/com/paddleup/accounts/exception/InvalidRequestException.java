package com.paddleup.accounts.exception;

/** The request is well-formed JSON but breaks a rule; maps to 400. */
public class InvalidRequestException extends RuntimeException {

  public InvalidRequestException(String message) {
    super(message);
  }
}
