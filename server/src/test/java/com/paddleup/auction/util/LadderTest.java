package com.paddleup.auction.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.auction.model.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AC-4: first bid is base price; the next bid adds the step of the band holding the current. */
class LadderTest {

  private static final List<Step> TWO_BANDS = List.of(new Step(0, 5), new Step(100, 10));

  @Test
  void theFirstBidIsTheBasePrice() {
    assertThat(Ladder.required(TWO_BANDS, 20, 0)).isEqualTo(20);
    assertThat(Ladder.first(75)).isEqualTo(75);
  }

  @Test
  void belowTheBandEdgeTheLowerStepApplies() {
    assertThat(Ladder.next(TWO_BANDS, 20)).isEqualTo(25);
    assertThat(Ladder.next(TWO_BANDS, 95)).isEqualTo(100);
    assertThat(Ladder.next(TWO_BANDS, 99)).isEqualTo(104);
  }

  @Test
  void atTheBandEdgeTheUpperStepApplies() {
    assertThat(Ladder.next(TWO_BANDS, 100)).isEqualTo(110);
    assertThat(Ladder.required(TWO_BANDS, 20, 100)).isEqualTo(110);
  }

  @Test
  void aboveTheBandEdgeTheUpperStepApplies() {
    assertThat(Ladder.next(TWO_BANDS, 105)).isEqualTo(115);
    assertThat(Ladder.next(TWO_BANDS, 5000)).isEqualTo(5010);
  }

  @Test
  void theOrderOfTheBandsDoesNotMatter() {
    assertThat(Ladder.next(List.of(new Step(100, 10), new Step(0, 5)), 100)).isEqualTo(110);
  }

  @Test
  void aLadderWithNoBandForTheAmountIsRefused() {
    assertThatThrownBy(() -> Ladder.next(List.of(new Step(50, 5)), 10))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
