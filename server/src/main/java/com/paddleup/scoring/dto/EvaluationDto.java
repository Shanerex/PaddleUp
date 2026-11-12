package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** The result of scoring one player in one match: the total and one line per rule that fired. */
public record EvaluationDto(
    @Schema(requiredMode = REQUIRED) BigDecimal total,
    @Schema(requiredMode = REQUIRED) List<PointsLineDto> lines) {

  public EvaluationDto {
    lines = lines == null ? List.of() : List.copyOf(lines);
  }
}
