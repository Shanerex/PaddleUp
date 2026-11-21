package com.paddleup.scoring.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paddleup.rooms.dto.Violation;
import com.paddleup.rooms.exception.RoomForbiddenException;
import com.paddleup.rooms.exception.RoomNotFoundException;
import com.paddleup.scoring.dto.SheetFieldErrorDto;
import java.util.List;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps scoring errors to {@code {error, message?, violations?}}. Room errors raised through the
 * rooms port keep rooms' own status: 404 for a non-member, 403 for a non-Commissioner.
 */
@RestControllerAdvice(basePackages = "com.paddleup.scoring.controller")
class ScoringExceptionHandler {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Body(
      String error, String message, List<Violation> violations, List<SheetFieldErrorDto> fields) {
    static Body of(String error, String message) {
      return new Body(error, message, null, null);
    }
  }

  @ExceptionHandler({RoomNotFoundException.class, TypeMismatchException.class})
  ResponseEntity<Body> roomNotFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Body.of("room_not_found", null));
  }

  @ExceptionHandler(RoomForbiddenException.class)
  ResponseEntity<Body> forbidden(RoomForbiddenException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Body.of("forbidden", e.getMessage()));
  }

  @ExceptionHandler(RuleSetConflictException.class)
  ResponseEntity<Body> conflict(RuleSetConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Body.of(e.code(), e.getMessage()));
  }

  @ExceptionHandler(InvalidRuleSetException.class)
  ResponseEntity<Body> invalid(InvalidRuleSetException e) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new Body("invalid_rule_set", e.getMessage(), e.violations(), null));
  }

  @ExceptionHandler(InvalidStatSheetException.class)
  ResponseEntity<Body> invalidSheet(InvalidStatSheetException e) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new Body(e.code(), e.getMessage(), null, e.fields()));
  }

  @ExceptionHandler(MatchRefusedException.class)
  ResponseEntity<Body> matchRefused(MatchRefusedException e) {
    return ResponseEntity.status(e.conflict() ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND)
        .body(Body.of(e.code(), e.getMessage()));
  }

  @ExceptionHandler(InvalidRequestException.class)
  ResponseEntity<Body> invalidRequest(InvalidRequestException e) {
    return ResponseEntity.badRequest().body(Body.of("invalid_request", e.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Body> unreadable() {
    return ResponseEntity.badRequest()
        .body(Body.of("invalid_request", "The request body could not be read"));
  }
}
