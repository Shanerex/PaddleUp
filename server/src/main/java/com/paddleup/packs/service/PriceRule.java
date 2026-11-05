package com.paddleup.packs.service;

import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.PresetDto;
import java.util.List;

/**
 * Which base prices a purse and increment ladder accept: no more than the purse, and a whole number
 * of steps above the start of the band that holds the price. A rule built from several limits
 * accepts only what every one accepts.
 */
public final class PriceRule {

  private record Limit(long purse, List<PresetDto.IncrementStep> ladder) {}

  private final List<Limit> limits;

  private PriceRule(List<Limit> limits) {
    this.limits = limits;
  }

  /** The purse and ladder of one room's config. */
  public static PriceRule of(long purse, List<PresetDto.IncrementStep> ladder) {
    return new PriceRule(List.of(new Limit(purse, List.copyOf(ladder))));
  }

  /** Every preset of the pack must accept the price. */
  public static PriceRule ofPack(PackDto pack) {
    return new PriceRule(
        pack.presets().stream().map(p -> new Limit(p.purse(), p.incrementLadder())).toList());
  }

  /** True when {@code price} fits every limit; a price of zero or less never does. */
  public boolean accepts(long price) {
    return price > 0 && limits.stream().allMatch(l -> fits(l, price));
  }

  private static boolean fits(Limit limit, long price) {
    if (price > limit.purse()) {
      return false;
    }
    PresetDto.IncrementStep band = null;
    for (PresetDto.IncrementStep step : limit.ladder()) {
      if (step.from() <= price && (band == null || step.from() > band.from())) {
        band = step;
      }
    }
    return band != null && band.step() > 0 && (price - band.from()) % band.step() == 0;
  }
}
