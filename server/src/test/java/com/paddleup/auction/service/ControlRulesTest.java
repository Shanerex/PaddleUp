package com.paddleup.auction.service;

import static com.paddleup.auction.service.AuctionFixtures.RESUME_LOBBY;
import static com.paddleup.auction.service.AuctionFixtures.T0;
import static com.paddleup.auction.service.AuctionFixtures.USER_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Control;
import com.paddleup.auction.model.Decision;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-4: what a pause freezes, from the state alone. */
class ControlRulesTest {

  private static AuctionEvent decide(AuctionState s, Control.Action action, java.time.Instant now) {
    var control = new Control(action, UUID.randomUUID(), USER_A, 0, null, null);
    return ((Decision.Accepted) ControlRules.decide(s, control, now, RESUME_LOBBY)).events().get(0);
  }

  @Test
  void aPauseOnTheOpenLotFreezesThatLotsClockAndNamesTheLot() {
    var s = AuctionFixtures.lotOneOpen();

    AuctionEvent paused = decide(s, Control.Action.PAUSE, T0.plusSeconds(5));

    assertThat(paused.lot()).isEqualTo(1);
    assertThat(paused.number("heldMs")).isEqualTo(15_000);
    assertThat(paused.payload()).doesNotContainKey("gap");
  }

  /** CR3-3: in a gap no lot clock runs; the event holds the gap and names no lot. */
  @Test
  void aPauseInAGapHoldsTheGapNamesNoLotAndResumeRestoresTheGap() {
    var open = AuctionFixtures.lotOneOpen();
    var gap = AuctionReducer.replay(open, AuctionFixtures.due(open, open.deadline()));
    var at = gap.nextOpenAt().minusSeconds(3);

    AuctionEvent paused = decide(gap, Control.Action.PAUSE, at);

    assertThat(paused.lot()).isNull();
    assertThat(paused.payload()).containsEntry("gap", true);
    assertThat(paused.number("heldMs")).isEqualTo(3000);
    var held = AuctionReducer.replay(gap, java.util.List.of(paused));
    assertThat(held.nextOpenAt()).isNull();
    assertThat(held.heldMs()).isEqualTo(3000);

    var resumedAt = at.plusSeconds(600);
    AuctionEvent resumed = decide(held, Control.Action.RESUME, resumedAt);
    var back = AuctionReducer.replay(held, java.util.List.of(resumed));
    assertThat(back.lotOpen()).isFalse();
    assertThat(back.nextOpenAt()).isEqualTo(resumedAt.plusSeconds(3));
  }
}
