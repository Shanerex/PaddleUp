package com.paddleup.packs.dto;

import java.util.List;

/**
 * One entry of the pack's stat schema. {@code eventTypes} lists the typed occurrences the stat can
 * carry (empty when it has none); {@code unit} says what one unit of the stat is, and may be null.
 */
public record StatDto(String id, String label, String kind, List<String> eventTypes, String unit) {

  public StatDto(String id, String label, String kind, List<String> eventTypes) {
    this(id, label, kind, eventTypes, null);
  }

  public StatDto {
    eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
  }
}
