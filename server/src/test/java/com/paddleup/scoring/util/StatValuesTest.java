package com.paddleup.scoring.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.StatDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** AC-6, AC-7: one scale for every value, and the range rules of a stat. */
class StatValuesTest {

  static final StatDto WHOLE = new StatDto("a", "A", "integer", List.of());
  static final StatDto DECIMAL = new StatDto("d", "D", "decimal", List.of());
  static final StatDto FLAG =
      new StatDto("f", "F", "integer", List.of(), null, null, BigDecimal.ZERO, BigDecimal.ONE);

  @Test
  void fiftyTwoInEveryFormIsTheSameStoredValue() {
    BigDecimal a = StatValues.normalise(new BigDecimal("52"));
    BigDecimal b = StatValues.normalise(new BigDecimal("52.0"));
    BigDecimal c = StatValues.normalise(new BigDecimal("52.0000"));
    BigDecimal d = StatValues.normalise(new BigDecimal("5.2E+1"));

    assertThat(a).isEqualTo(b).isEqualTo(c).isEqualTo(d);
    assertThat(a.scale()).isZero();
    assertThat(StatValues.normalise(new BigDecimal("0.000"))).isEqualTo(BigDecimal.ZERO);
    assertThat(StatValues.normalise(new BigDecimal("1.50")).toPlainString()).isEqualTo("1.5");
  }

  @ParameterizedTest(name = "{0} on a whole stat: {1}")
  @CsvSource({
    "52, ok",
    "52.0000, ok",
    "1000000, ok",
    "0, ok",
    "-1, below_min",
    "1000001, above_max",
    "2.5, not_whole",
    "0.00001, too_many_decimals",
    "1.23456, too_many_decimals"
  })
  void wholeStatRules(String value, String reason) {
    var check = StatValues.check(WHOLE, new BigDecimal(value));

    assertThat(check.reason()).isEqualTo("ok".equals(reason) ? null : reason);
    assertThat(check.valid()).isEqualTo("ok".equals(reason));
  }

  @Test
  void aDecimalStatTakesUpToFourDecimals() {
    assertThat(StatValues.check(DECIMAL, new BigDecimal("2.5")).valid()).isTrue();
    assertThat(StatValues.check(DECIMAL, new BigDecimal("2.5000")).value())
        .isEqualTo(new BigDecimal("2.5"));
    assertThat(StatValues.check(DECIMAL, new BigDecimal("2.12345")).reason())
        .isEqualTo("too_many_decimals");
  }

  @Test
  void thePackLimitsReplaceTheDefaultRange() {
    assertThat(StatValues.min(FLAG)).isEqualByComparingTo("0");
    assertThat(StatValues.max(FLAG)).isEqualByComparingTo("1");
    assertThat(StatValues.max(WHOLE)).isEqualByComparingTo("1000000");
    assertThat(StatValues.check(FLAG, BigDecimal.ONE).valid()).isTrue();
    assertThat(StatValues.check(FLAG, new BigDecimal("2")).reason()).isEqualTo("above_max");
  }

  @Test
  void aMissingValueIsRefused() {
    assertThat(StatValues.check(WHOLE, null).reason()).isEqualTo("required");
  }

  @ParameterizedTest(name = "{0} is refused as {1} without building the number")
  @CsvSource({
    "1e999999999, above_max",
    "-1e999999999, below_min",
    "1E+2147483647, above_max",
    "-1E+2147483647, below_min",
    "1e9, above_max",
    "-1e9, below_min",
    "1e-999999999, too_many_decimals",
    "-1e-999999999, too_many_decimals"
  })
  void anAbsurdExponentIsRefusedQuickly(String value, String reason) {
    var started = System.nanoTime();
    var check = StatValues.check(WHOLE, new BigDecimal(value));

    assertThat(check.reason()).isEqualTo(reason);
    assertThat(System.nanoTime() - started).isLessThan(100_000_000L);
  }

  @Test
  void zeroInAnyExponentIsStillZero() {
    assertThat(StatValues.check(WHOLE, new BigDecimal("0E+999999999")).value())
        .isEqualByComparingTo("0");
    assertThat(StatValues.check(WHOLE, new BigDecimal("1E+6")).valid()).isTrue();
    assertThat(StatValues.check(WHOLE, new BigDecimal("1E+6")).value())
        .isEqualByComparingTo("1000000");
  }
}
