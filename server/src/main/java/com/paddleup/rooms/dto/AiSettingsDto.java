package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;

/** Room-level AI settings: the master switch, lifelines per type and report card visibility. */
public record AiSettingsDto(
    @Schema(requiredMode = REQUIRED) boolean enabled,
    @Schema(requiredMode = REQUIRED) Map<String, Integer> lifelines,
    @Schema(requiredMode = REQUIRED) String reportCardVisibility) {

  public AiSettingsDto {
    lifelines = lifelines == null ? Map.of() : new LinkedHashMap<>(lifelines);
  }
}
