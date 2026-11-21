package com.paddleup.scoring.dto;

import java.util.List;

/** The fields whose manual value is to be removed. */
public record ClearFieldsRequest(List<FieldRefDto> fields) {
  public ClearFieldsRequest {
    fields = fields == null ? List.of() : List.copyOf(fields);
  }
}
