package com.paddleup.packs.dto;

import java.util.List;

/**
 * One entry of the pack's stat schema. {@code eventTypes} lists the typed occurrences the stat can
 * carry (empty when it has none).
 */
public record StatDto(String id, String label, String kind, List<String> eventTypes) {
  public StatDto {
    eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
  }
}
