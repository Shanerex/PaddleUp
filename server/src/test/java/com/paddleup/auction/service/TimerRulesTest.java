package com.paddleup.auction.service;

import static com.paddleup.auction.service.AuctionFixtures.A;
import static com.paddleup.auction.service.AuctionFixtures.T0;
import static com.paddleup.auction.service.AuctionFixtures.USER_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Decision;
import com.paddleup.auction.model.EventKind;
import com.paddleup.auction.model.Intent;
import com.paddleup.auction.model.Phase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-8, AC-14: what the clock alone does. */
class TimerRulesTest {

  private static AuctionState withBid() {
    var s = AuctionFixtures.lotOneOpen();
    var d =
        (Decision.Accepted)
            IntentRules.decide(
                s,
                Intent.bid(UUID.randomUUID(), USER_A, A, 1, 20),
                T0.plusSeconds(2),
                AuctionFixtures.GUARDS);
    return AuctionReducer.replay(s, d.events());
  }

  @Test
  void nothingIsDueBeforeTheDeadline() {
    assertThat(TimerRules.due(withBid(), T0.plusSeconds(11))).isEmpty();
  }

  @Test
  void atTheDeadlineTheLeaderWinsAtTheBidPrice() {
    var s = withBid();

    List<AuctionEvent> due = TimerRules.due(s, T0.plusSeconds(12));

    assertThat(due).hasSize(1);
    assertThat(due.get(0).kind()).isEqualTo(EventKind.LOT_SOLD);
    assertThat(due.get(0).franchiseId()).isEqualTo(A);
    assertThat(due.get(0).number("amount")).isEqualTo(20);
    var after = AuctionReducer.replay(s, due);
    assertThat(after.purses().get(A)).isEqualTo(980);
    assertThat(after.squads().get(A)).hasSize(1);
  }

  @Test
  void withNoBidsTheLotIsUnsoldAndTheNextOpensAfterTheGap() {
    var s = AuctionFixtures.lotOneOpen();

    var closed = AuctionReducer.replay(s, TimerRules.due(s, T0.plusSeconds(20)));

    assertThat(closed.unsold()).containsExactly(1);
    assertThat(TimerRules.due(closed, T0.plusSeconds(24))).isEmpty();
    var due = TimerRules.due(closed, T0.plusSeconds(25));
    assertThat(due).hasSize(1);
    assertThat(due.get(0).kind()).isEqualTo(EventKind.LOT_OPENED);
    assertThat(due.get(0).lot()).isEqualTo(2);
    assertThat(due.get(0).instant("deadline")).isEqualTo(T0.plusSeconds(45));
  }

  @Test
  void theLastCloseEndsTheAuctionWithUnsoldListed() {
    var s = AuctionFixtures.lotOneOpen();
    s = AuctionReducer.replay(s, TimerRules.due(s, s.deadline()));
    s = AuctionReducer.replay(s, TimerRules.due(s, s.nextOpenAt()));
    s = AuctionReducer.replay(s, TimerRules.due(s, s.deadline()));
    s = AuctionReducer.replay(s, TimerRules.due(s, s.nextOpenAt()));

    var due = TimerRules.due(s, s.deadline());

    assertThat(due)
        .extracting(AuctionEvent::kind)
        .containsExactly(EventKind.LOT_UNSOLD, EventKind.AUCTION_ENDED);
    var end = AuctionReducer.replay(s, due);
    assertThat(end.phase()).isEqualTo(Phase.ENDED);
    assertThat(end.unsold()).containsExactly(1, 2, 3);
    assertThat(TimerRules.due(end, T0.plusSeconds(9999))).isEmpty();
  }

  @Test
  void aLotWhoseDeadlinePassedInDowntimeResumesWithAFreshTBid() {
    var s = withBid();

    assertThat(TimerRules.resume(s, T0.plusSeconds(11))).isEmpty();
    var resume = TimerRules.resume(s, T0.plusSeconds(500));

    assertThat(resume).hasSize(1);
    assertThat(resume.get(0).kind()).isEqualTo(EventKind.LOT_RESUMED);
    assertThat(resume.get(0).instant("deadline")).isEqualTo(T0.plusSeconds(510));
    var after = AuctionReducer.replay(s, resume);
    assertThat(after.leader()).isEqualTo(A);
    assertThat(after.currentBid()).isEqualTo(20);
  }
}
