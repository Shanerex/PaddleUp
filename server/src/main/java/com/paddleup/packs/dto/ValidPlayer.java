package com.paddleup.packs.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** A player that passed every row rule: cleaned text, known role and tags, a whole price. */
public record ValidPlayer(
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(requiredMode = REQUIRED) String country,
    @Schema(requiredMode = REQUIRED) String role,
    @Schema(requiredMode = REQUIRED) List<String> tags,
    @Schema(requiredMode = REQUIRED) long basePrice,
    @Schema(requiredMode = REQUIRED) String set,
    @Schema(nullable = true) String providerId) {

  public ValidPlayer {
    tags = List.copyOf(tags);
  }
}
