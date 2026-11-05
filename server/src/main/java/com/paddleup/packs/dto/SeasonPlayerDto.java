package com.paddleup.packs.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** One player of a season dataset version. Immutable once imported. */
public record SeasonPlayerDto(
    @Schema(requiredMode = REQUIRED) UUID id,
    @Schema(requiredMode = REQUIRED) int position,
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(requiredMode = REQUIRED) String country,
    @Schema(requiredMode = REQUIRED) String role,
    @Schema(requiredMode = REQUIRED) List<String> tags,
    @Schema(requiredMode = REQUIRED) long basePrice,
    @Schema(requiredMode = REQUIRED) String set,
    @Schema(nullable = true) String providerId) {

  public SeasonPlayerDto {
    tags = List.copyOf(tags);
  }
}
