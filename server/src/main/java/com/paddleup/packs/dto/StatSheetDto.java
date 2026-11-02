package com.paddleup.packs.dto;

import java.util.List;

/** The stats of one completed match, in the pack's stat schema. */
public record StatSheetDto(String externalMatchId, List<StatLineDto> lines) {
  public StatSheetDto {
    lines = lines == null ? List.of() : List.copyOf(lines);
  }
}
