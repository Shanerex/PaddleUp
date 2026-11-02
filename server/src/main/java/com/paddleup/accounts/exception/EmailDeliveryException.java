package com.paddleup.accounts.exception;

/** The email provider rejected or could not receive a message. */
public class EmailDeliveryException extends RuntimeException {

  public EmailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
