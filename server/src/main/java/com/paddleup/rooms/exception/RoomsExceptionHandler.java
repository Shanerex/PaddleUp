package com.paddleup.rooms.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paddleup.rooms.dto.Violation;
import java.util.List;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps rooms errors to {@code {error, message?, field?, violations?}}. */
@RestControllerAdvice(basePackages = "com.paddleup.rooms.controller")
class RoomsExceptionHandler {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Body(String error, String message, String field, List<Violation> violations) {
    static Body of(String error, String message) {
      return new Body(error, message, null, null);
    }
  }

  @ExceptionHandler({RoomNotFoundException.class, TypeMismatchException.class})
  ResponseEntity<Body> notFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Body.of("room_not_found", null));
  }

  @ExceptionHandler(RoomForbiddenException.class)
  ResponseEntity<Body> forbidden(RoomForbiddenException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Body.of("forbidden", e.getMessage()));
  }

  @ExceptionHandler(RoomConflictException.class)
  ResponseEntity<Body> conflict(RoomConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Body.of(e.code(), e.getMessage()));
  }

  @ExceptionHandler(InvalidConfigException.class)
  ResponseEntity<Body> invalidConfig(InvalidConfigException e) {
    return ResponseEntity.badRequest()
        .body(new Body("invalid_config", e.getMessage(), null, e.violations()));
  }

  @ExceptionHandler(PoolRejectedException.class)
  ResponseEntity<Body> poolRejected(PoolRejectedException e) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new Body("invalid_player", e.getMessage(), null, e.violations()));
  }

  @ExceptionHandler(PoolPlayerNotFoundException.class)
  ResponseEntity<Body> playerNotFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Body.of("player_not_found", null));
  }

  @ExceptionHandler(RoomsRequestException.class)
  ResponseEntity<Body> invalidRequest(RoomsRequestException e) {
    return ResponseEntity.badRequest()
        .body(new Body("invalid_request", e.getMessage(), e.field(), null));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Body> unreadable() {
    return ResponseEntity.badRequest()
        .body(Body.of("invalid_request", "The request body could not be read"));
  }

  @ExceptionHandler(InvalidInviteException.class)
  ResponseEntity<Body> invalidInvite() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Body.of("invalid_invite", null));
  }

  @ExceptionHandler(NoInviteException.class)
  ResponseEntity<Body> noInvite() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Body.of("no_invite", null));
  }

  @ExceptionHandler(TooManyJoinAttemptsException.class)
  ResponseEntity<Body> tooMany(TooManyJoinAttemptsException e) {
    long seconds = (e.retryAfter().toMillis() + 999) / 1000;
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
        .body(Body.of("too_many_requests", null));
  }
}
