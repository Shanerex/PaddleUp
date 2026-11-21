package com.paddleup.scoring.exception;

import com.paddleup.scoring.dto.SheetFieldErrorDto;
import java.util.List;

/**
 * A stat sheet was refused whole: {@code code} is {@code invalid_stat_sheet} (the fields name what
 * is wrong) or {@code empty_sheet} (no field was filled). Maps to 422.
 */
public class InvalidStatSheetException extends RuntimeException {

  public static final String INVALID = "invalid_stat_sheet";
  public static final String EMPTY = "empty_sheet";

  private final String code;
  private final transient List<SheetFieldErrorDto> fields;

  public InvalidStatSheetException(String code, String message, List<SheetFieldErrorDto> fields) {
    super(message);
    this.code = code;
    this.fields = List.copyOf(fields);
  }

  public String code() {
    return code;
  }

  public List<SheetFieldErrorDto> fields() {
    return fields;
  }
}
