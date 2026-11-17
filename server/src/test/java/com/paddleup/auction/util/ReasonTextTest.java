package com.paddleup.auction.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** AC-7: the re-open reason is cleaned before it is stored or shown. */
class ReasonTextTest {

  @Test
  void markupControlCharactersAndRepeatedSpacesAreRemoved() {
    assertThat(ReasonText.sanitise("<b>Wrong</b>\u0007   paddle\n\tshown <script>x()</script>"))
        .isEqualTo("Wrong paddle shown");
  }

  @Test
  void missingBlankOrHugeInputIsEmpty() {
    assertThat(ReasonText.sanitise(null)).isEmpty();
    assertThat(ReasonText.sanitise("  <i> </i> ")).isEmpty();
    assertThat(ReasonText.sanitise("a".repeat(4001))).isEmpty();
  }

  @Test
  void entitiesAreDecodedAndAngleBracketsDropped() {
    assertThat(ReasonText.sanitise("Tom &amp; Jerry &lt;3")).isEqualTo("Tom & Jerry 3");
  }

  @Test
  void theCapIs140CodePointsAndInputBeyondTheParseLimitIsTooLong() {
    assertThat(ReasonText.MAX_LENGTH).isEqualTo(140);
    assertThat(ReasonText.tooLong("x".repeat(140))).isFalse();
    assertThat(ReasonText.tooLong("x".repeat(141))).isTrue();
    // 140 table tennis emoji are 140 code points, though 280 UTF-16 units.
    assertThat(ReasonText.tooLong("\uD83C\uDFD3".repeat(140))).isFalse();
    assertThat(ReasonText.tooLong("\uD83C\uDFD3".repeat(141))).isTrue();
    assertThat(ReasonText.tooLong("a".repeat(4001))).isTrue();
    assertThat(ReasonText.tooLong(null)).isFalse();
    assertThat(ReasonText.tooLong("   ")).isFalse();
  }
}
