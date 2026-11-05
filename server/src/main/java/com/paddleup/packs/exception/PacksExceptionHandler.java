package com.paddleup.packs.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paddleup.packs.dto.ImportReportDto;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Maps packs errors to {@code {error, message?, reason?, report?}}. */
@RestControllerAdvice(basePackages = "com.paddleup.packs.controller")
class PacksExceptionHandler {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Body(String error, String message, String reason, ImportReportDto report) {}

  @ExceptionHandler(NotAdminException.class)
  ResponseEntity<Body> forbidden(NotAdminException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new Body("forbidden", e.getMessage(), null, null));
  }

  @ExceptionHandler(NoValidRowsException.class)
  ResponseEntity<Body> noValidRows(NoValidRowsException e) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new Body("no_valid_rows", e.getMessage(), null, e.report()));
  }

  @ExceptionHandler(DatasetFileException.class)
  ResponseEntity<Body> badFile(DatasetFileException e) {
    return ResponseEntity.badRequest()
        .body(
            new Body(
                "invalid_file", e.getMessage(), e.reason().name().toLowerCase(Locale.ROOT), null));
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MissingServletRequestPartException.class
  })
  ResponseEntity<Body> missing() {
    return ResponseEntity.badRequest()
        .body(new Body("invalid_request", "Choose a pack and a file", null, null));
  }

  @ExceptionHandler(PackNotFoundException.class)
  ResponseEntity<Body> unknownPack(PackNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new Body("pack_not_found", e.getMessage(), null, null));
  }
}
