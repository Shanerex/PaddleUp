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
    Retention retention) {

  public PresetDto {
    incrementLadder = incrementLadder == null ? List.of() : List.copyOf(incrementLadder);
    roleMinimums = roleMinimums == null ? Map.of() : Map.copyOf(roleMinimums);
  }

  /** Amounts are shown as the value divided by {@code divisor}, followed by {@code label}. */
  public record DisplayUnit(String label, long divisor) {}

  /** From bid {@code from} upward, a bid rises in steps of {@code step}. */
  public record IncrementStep(long from, long step) {}

  public record Retention(int maxRetentions, int rtmCards) {}
}
