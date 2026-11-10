package com.paddleup.packs.dto;

import java.util.List;

/**
 * One entry of the pack's stat schema. {@code eventTypes} lists the typed occurrences the stat can
 * carry (empty when it has none); {@code unit} says what one unit of the stat is, and may be null.
 * {@code group} names the heading the stat is listed under; null falls back to the core grouping.
 */
public record StatDto(
    String id, String label, String kind, List<String> eventTypes, String unit, String group) {

  public StatDto(String id, String label, String kind, List<String> eventTypes, String unit) {
    this(id, label, kind, eventTypes, unit, null);
  }

  public StatDto(String id, String label, String kind, List<String> eventTypes) {
    this(id, label, kind, eventTypes, null, null);
  }

  public StatDto {
    eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
  }
}
