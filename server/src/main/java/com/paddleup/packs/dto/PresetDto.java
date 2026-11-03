package com.paddleup.packs.dto;

import java.util.List;
import java.util.Map;

/**
 * A room preset. Amounts are whole numbers of the smallest unit of {@code currencyCode}; the
 * display unit says how to show them.
 */
public record PresetDto(
    String id,
    String label,
    long purse,
    String currencyCode,
    DisplayUnit displayUnit,
    List<IncrementStep> incrementLadder,
    int squadMin,
    int squadMax,
    Map<String, Integer> roleMinimums,
    Retention retention,
    Integer poolSize) {

  public PresetDto(
      String id,
      String label,
      long purse,
      String currencyCode,
      DisplayUnit displayUnit,
      List<IncrementStep> incrementLadder,
      int squadMin,
      int squadMax,
      Map<String, Integer> roleMinimums,
      Retention retention) {
    this(
        id,
        label,
        purse,
        currencyCode,
        displayUnit,
        incrementLadder,
        squadMin,
        squadMax,
        roleMinimums,
        retention,
        null);
  }

  public PresetDto {
    incrementLadder = copyOf("incrementLadder", incrementLadder);
    roleMinimums = copyOf("roleMinimums", roleMinimums);
  }

  /** Immutable copy; null reads as empty and a null element or value is rejected by name. */
  private static <T> List<T> copyOf(String field, List<T> list) {
    if (list == null) {
      return List.of();
    }
    if (list.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException(field + " must not contain null");
    }
    return List.copyOf(list);
  }

  private static <K, V> Map<K, V> copyOf(String field, Map<K, V> map) {
    if (map == null) {
      return Map.of();
    }
    if (map.values().stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException(field + " must not contain a null value");
    }
    return Map.copyOf(map);
  }

  /** Amounts are shown as the value divided by {@code divisor}, followed by {@code label}. */
  public record DisplayUnit(String label, long divisor) {}

  /** From bid {@code from} upward, a bid rises in steps of {@code step}. */
  public record IncrementStep(long from, long step) {}

  /**
   * Retention limits. The remaining fields are optional: {@code tagLimits} caps retentions per tag,
   * {@code slabs} lists the price of each retention pick per tag, {@code rtmFromSeason} is the
   * first season a right to match is available and {@code rtmCounterRaise} says whether the
   * original holder may raise after a counter. Missing values read as empty or null.
   */
  public record Retention(
      int maxRetentions,
      int rtmCards,
      Map<String, Integer> tagLimits,
      List<Slab> slabs,
      Integer rtmFromSeason,
      Boolean rtmCounterRaise) {

    public Retention(int maxRetentions, int rtmCards) {
      this(maxRetentions, rtmCards, null, null, null, null);
    }

    public Retention {
      tagLimits = copyOf("retention.tagLimits", tagLimits);
      slabs = copyOf("retention.slabs", slabs);
    }
  }

  /** The price of each successive retention pick of one tag, in the smallest currency unit. */
  public record Slab(String tag, List<Long> prices) {
    public Slab {
      prices = copyOf("slab prices", prices);
    }
  }
}
