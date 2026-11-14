package com.paddleup.scoring.exception;

/** The request is malformed (for example a required field is missing); maps to 400. */
public class InvalidRequestException extends RuntimeException {

  public InvalidRequestException(String message) {
    super(message);
  }
}
