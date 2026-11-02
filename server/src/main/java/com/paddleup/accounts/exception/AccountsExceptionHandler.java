package com.paddleup.accounts.exception;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.paddleup.accounts.controller")
class AccountsExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(AccountsExceptionHandler.class);

  @ExceptionHandler(InvalidLoginException.class)
  ResponseEntity<Map<String, String>> invalidLogin() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_login"));
  }

  @ExceptionHandler(InvalidRequestException.class)
  ResponseEntity<Map<String, String>> invalidRequest(InvalidRequestException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "invalid_request", "message", e.getMessage()));
  }

  @ExceptionHandler(TooManyRequestsException.class)
  ResponseEntity<Map<String, String>> tooMany(TooManyRequestsException e) {
    long seconds = (e.retryAfter().toMillis() + 999) / 1000;
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
        .body(Map.of("error", "too_many_requests"));
  }

  @ExceptionHandler(EmailDeliveryException.class)
  ResponseEntity<Map<String, String>> emailDown(EmailDeliveryException e) {
    log.error("Sign-in email could not be delivered", e);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("error", "email_unavailable"));
  }
}
