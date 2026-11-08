package com.paddleup.auction.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paddleup.rooms.exception.RoomConflictException;
import com.paddleup.rooms.exception.RoomForbiddenException;
import com.paddleup.rooms.exception.RoomNotFoundException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps auction errors to {@code {error, message?, field?}}. Room errors raised through the rooms
 * port keep rooms' own status: 404 for a non-member, 403 for a non-Commissioner.
 */
@RestControllerAdvice(basePackages = "com.paddleup.auction.controller")
class AuctionExceptionHandler {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Body(String error, String message, String field) {
    static Body of(String error, String message) {
      return new Body(error, message, null);
    }
  }

  @ExceptionHandler({RoomNotFoundException.class, TypeMismatchException.class})
  ResponseEntity<Body> roomNotFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Body.of("room_not_found", null));
  }

  @ExceptionHandler(AuctionNotFoundException.class)
  ResponseEntity<Body> noAuction(AuctionNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Body.of("no_auction", e.getMessage()));
  }

  @ExceptionHandler(RoomForbiddenException.class)
  ResponseEntity<Body> forbidden(RoomForbiddenException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Body.of("forbidden", e.getMessage()));
  }

  @ExceptionHandler(AuctionConflictException.class)
  ResponseEntity<Body> conflict(AuctionConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Body.of(e.code(), e.getMessage()));
  }

  @ExceptionHandler(RoomConflictException.class)
  ResponseEntity<Body> roomConflict(RoomConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Body.of(e.code(), e.getMessage()));
  }

  @ExceptionHandler(AuctionRequestException.class)
  ResponseEntity<Body> invalidRequest(AuctionRequestException e) {
    return ResponseEntity.badRequest().body(new Body("invalid_request", e.getMessage(), e.field()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Body> unreadable() {
    return ResponseEntity.badRequest()
        .body(Body.of("invalid_request", "The request body could not be read"));
  }

  @ExceptionHandler(LogWriteException.class)
  ResponseEntity<Body> logWrite(LogWriteException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Body.of("log_unavailable", "The bid could not be recorded; try again"));
  }

  @ExceptionHandler(AuctionBusyException.class)
  ResponseEntity<Body> busy(AuctionBusyException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Body.of("busy", e.getMessage()));
  }
}
