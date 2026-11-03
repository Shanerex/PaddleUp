package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** A pack the Commissioner can start a room from, with its presets. */
public record PackChoiceDto(
    @Schema(requiredMode = REQUIRED) String id,
    @Schema(requiredMode = REQUIRED) String version,
    @Schema(requiredMode = REQUIRED) List<Preset> presets,
    @Schema(nullable = true) String name,
    @Schema(nullable = true) String summary,
    @Schema(nullable = true) String icon) {

  public record Preset(
      @Schema(requiredMode = REQUIRED) String id, @Schema(requiredMode = REQUIRED) String label) {}
}
