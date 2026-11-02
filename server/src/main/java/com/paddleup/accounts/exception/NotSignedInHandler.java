package com.paddleup.accounts.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers 401 wherever {@code CurrentUser.require()} throws. Not limited to the accounts
 * controllers, because every module's controllers call it.
 */
@RestControllerAdvice
class NotSignedInHandler {

  @ExceptionHandler(NotSignedInException.class)
  ResponseEntity<Map<String, String>> notSignedIn() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorised"));
  }
}
