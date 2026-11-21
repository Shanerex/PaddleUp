package com.paddleup.scoring.util;

import com.paddleup.packs.dto.StatDto;
import java.math.BigDecimal;

/**
 * Normalises a stat value to one scale and checks it against its stat's limits. A stat's own min
 * and max (from the pack) win; otherwise the range is 0 to 1,000,000. Stateless.
 */
public final class StatValues {

  public static final BigDecimal DEFAULT_MIN = BigDecimal.ZERO;
  public static final BigDecimal DEFAULT_MAX = BigDecimal.valueOf(1_000_000);
  public static final int DECIMALS_MAX = 4;

  private StatValues() {}

  /** The outcome of a check: the normalised value, or the reason it was refused. */
  public record Check(BigDecimal value, String reason) {
    public boolean valid() {
      return reason == null;
    }
  }

  /** Same number, no trailing zeros and no negative scale, so 52, 52.0 and 5.2E+1 are equal. */
  public static BigDecimal normalise(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }

  public static BigDecimal min(StatDto stat) {
    return stat.min() == null ? DEFAULT_MIN : stat.min();
  }

  public static BigDecimal max(StatDto stat) {
    return stat.max() == null ? DEFAULT_MAX : stat.max();
  }

  /**
   * Checks one entered value. Reasons: {@code required}, {@code too_many_decimals}, {@code
   * not_whole}, {@code below_min}, {@code above_max}.
   */
  public static Check check(StatDto stat, BigDecimal raw) {
    if (raw == null) {
      return new Check(null, "required");
    }
    Check outOfRange = magnitudeRefusal(stat, raw);
    if (outOfRange != null) {
      return outOfRange;
    }
    BigDecimal value = normalise(raw);
    if (value.scale() > DECIMALS_MAX) {
      return new Check(null, "too_many_decimals");
    }
    if (stat.whole() && value.scale() > 0) {
      return new Check(null, "not_whole");
    }
    if (value.compareTo(min(stat)) < 0) {
      return new Check(null, "below_min");
    }
    if (value.compareTo(max(stat)) > 0) {
      return new Check(null, "above_max");
    }
    return new Check(value, null);
  }

  /**
   * Refuses a value whose whole-number digits alone put it past the stat's range, from its
   * precision and scale, so a huge exponent such as {@code 1e999999999} is never expanded.
   */
  private static Check magnitudeRefusal(StatDto stat, BigDecimal raw) {
    if (raw.signum() == 0) {
      return null;
    }
    long wholeDigits = (long) raw.precision() - raw.scale();
    long allowed =
        Math.max(digits(min(stat).abs().toBigInteger()), digits(max(stat).abs().toBigInteger()));
    if (wholeDigits <= allowed) {
      return null;
    }
    return new Check(null, raw.signum() > 0 ? "above_max" : "below_min");
  }

  private static long digits(java.math.BigInteger whole) {
    return whole.signum() == 0 ? 0 : whole.toString().length();
  }
}
