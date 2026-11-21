package com.paddleup.scoring.dto;

import java.util.List;

/** The fields the Commissioner filled in for one player and match. Blank fields are left out. */
public record SaveSheetRequest(List<FieldValueDto> fields) {
  public SaveSheetRequest {
    fields = fields == null ? List.of() : List.copyOf(fields);
  }
}
