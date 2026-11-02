package com.paddleup.packs.dto;

import java.util.List;

/** The pack's default scoring rules, in file order. */
public record RuleSetDto(List<RuleDto> rules) {
  public RuleSetDto {
    rules = rules == null ? List.of() : List.copyOf(rules);
  }
}
