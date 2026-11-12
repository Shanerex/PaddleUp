package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** What a change did to the rule set, by rule id. */
public record RuleDiffDto(
    @Schema(requiredMode = REQUIRED) List<String> added,
    @Schema(requiredMode = REQUIRED) List<String> removed,
    @Schema(requiredMode = REQUIRED) List<String> changed) {

  public RuleDiffDto {
    added = added == null ? List.of() : List.copyOf(added);
    removed = removed == null ? List.of() : List.copyOf(removed);
    changed = changed == null ? List.of() : List.copyOf(changed);
  }
}
