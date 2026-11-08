package com.paddleup.packs.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * What an import did. {@code version} is the new dataset version, or null when no row was valid and
 * nothing was stored. {@code rows} lists the first problems; {@code moreProblems} counts the rest.
 */
public record ImportReportDto(
    @Schema(nullable = true) Integer version,
    @Schema(requiredMode = REQUIRED) String packId,
    @Schema(requiredMode = REQUIRED) String packVersion,
    @Schema(requiredMode = REQUIRED) int totalRows,
    @Schema(requiredMode = REQUIRED) int imported,
    @Schema(requiredMode = REQUIRED) int skipped,
    @Schema(requiredMode = REQUIRED) List<RejectedRowDto> rows,
    @Schema(requiredMode = REQUIRED) int moreProblems) {

  public ImportReportDto {
    rows = List.copyOf(rows);
  }
}
