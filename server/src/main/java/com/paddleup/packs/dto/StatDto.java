package com.paddleup.packs.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One entry of the pack's stat schema. {@code eventTypes} lists the typed occurrences the stat can
 * carry (empty when it has none); {@code unit} says what one unit of the stat is, and may be null.
 * {@code group} names the heading the stat is listed under; null falls back to the core grouping.
 * {@code min} and {@code max} are the optional limits a manual entry must keep to; null means the
 * default range, and snapshots written before they existed read them as null.
 */
public record StatDto(
    String id,
    String label,
    String kind,
    List<String> eventTypes,
    String unit,
    String group,
    BigDecimal min,
    BigDecimal max) {

  public StatDto(
      String id, String label, String kind, List<String> eventTypes, String unit, String group) {
    this(id, label, kind, eventTypes, unit, group, null, null);
  }

  public StatDto(String id, String label, String kind, List<String> eventTypes, String unit) {
    this(id, label, kind, eventTypes, unit, null, null, null);
  }

  public StatDto(String id, String label, String kind, List<String> eventTypes) {
    this(id, label, kind, eventTypes, null, null, null, null);
  }

  /** True for a stat that holds whole numbers only: every kind except a decimal one. */
  public boolean whole() {
    return !"decimal".equals(kind);
  }

  public StatDto {
    eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
  }
}
