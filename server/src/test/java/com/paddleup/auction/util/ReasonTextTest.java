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
}
