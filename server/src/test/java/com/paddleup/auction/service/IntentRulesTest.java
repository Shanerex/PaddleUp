package com.paddleup.auction.service;

import static com.paddleup.auction.service.AuctionFixtures.A;
import static com.paddleup.auction.service.AuctionFixtures.B;
import static com.paddleup.auction.service.AuctionFixtures.GUARDS;
import static com.paddleup.auction.service.AuctionFixtures.POOR;
import static com.paddleup.auction.service.AuctionFixtures.T0;
import static com.paddleup.auction.service.AuctionFixtures.USER_A;
import static com.paddleup.auction.service.AuctionFixtures.USER_B;
import static com.paddleup.auction.service.AuctionFixtures.USER_POOR;
import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Decision;
import com.paddleup.auction.model.EventKind;
import com.paddleup.auction.model.Intent;
import com.paddleup.auction.model.Reason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-4, AC-5, AC-6: the pure rules for a bid and a pass. */
class IntentRulesTest {

  private static final Instant NOW = T0.plusSeconds(3);

  private static Decision bid(AuctionState s, UUID franchise, UUID user, int lot, long amount) {
    return IntentRules.decide(
        s, Intent.bid(UUID.randomUUID(), user, franchise, lot, amount), NOW, GUARDS);
  }

  private static Decision pass(AuctionState s, UUID franchise, UUID user, int lot) {
    return IntentRules.decide(s, Intent.pass(UUID.randomUUID(), user, franchise, lot), NOW, GUARDS);
  }

  private static AuctionState apply(AuctionState s, Decision d) {
    return AuctionReducer.replay(s, ((Decision.Accepted) d).events());
  }

  private static void assertRejected(Decision d, String reason) {
    assertThat(d).isEqualTo(new Decision.Rejected(reason));
  }

  @Test
  void theFirstBidAtBasePriceIsAcceptedAndSetsDeadlineToNowPlusTBid() {
    var s = AuctionFixtures.lotOneOpen();

    var decision = bid(s, A, USER_A, 1, 20);

    assertThat(decision).isInstanceOf(Decision.Accepted.class);
    List<AuctionEvent> events = ((Decision.Accepted) decision).events();
    assertThat(events).hasSize(1);
    assertThat(events.get(0).kind()).isEqualTo(EventKind.BID_ACCEPTED);
    assertThat(events.get(0).seq()).isEqualTo(3);
    assertThat(events.get(0).instant("deadline")).isEqualTo(NOW.plusSeconds(10));
    var after = apply(s, decision);
    assertThat(after.leader()).isEqualTo(A);
    assertThat(after.currentBid()).isEqualTo(20);
    assertThat(after.deadline()).isEqualTo(NOW.plusSeconds(10));
  }

  @Test
  void onlyTheExactNextAmountIsAcceptedBelowAtAndAboveABandEdge() {
    var s = AuctionFixtures.lotOneOpen();
    for (long wrong : new long[] {0, 5, 19, 21, 25, 100}) {
      assertRejected(bid(s, A, USER_A, 1, wrong), Reason.OUTBID);
    }
    // 20 -> 25 -> ... -> 95 (+5) -> 100 (band edge, +5) -> 110 (+10) -> 120.
    var state = s;
    long current = 20;
    UUID[] bidders = {A, B};
    int turn = 0;
    while (current < 120) {
      UUID who = bidders[turn++ % 2];
      UUID user = who.equals(A) ? USER_A : USER_B;
      state = apply(state, bid(state, who, user, 1, current));
      long step = current < 100 ? 5 : 10;
      assertRejected(bid(state, bidders[turn % 2], USER_B, 1, current), Reason.OUTBID);
      assertRejected(bid(state, bidders[turn % 2], USER_B, 1, current + step - 1), Reason.OUTBID);
      assertRejected(bid(state, bidders[turn % 2], USER_B, 1, current + step + 1), Reason.OUTBID);
      current += step;
    }
    assertThat(state.currentBid()).isEqualTo(110);
  }

  @Test
  void aBidByTheLeaderIsRejectedWithNoEvent() {
    var s =
        apply(AuctionFixtures.lotOneOpen(), bid(AuctionFixtures.lotOneOpen(), A, USER_A, 1, 20));

    assertRejected(bid(s, A, USER_A, 1, 25), Reason.LEADER);
  }

  @Test
  void aBidByANonFranchiseIsRejected() {
    var s = AuctionFixtures.lotOneOpen();

    assertRejected(bid(s, null, UUID.randomUUID(), 1, 20), Reason.NOT_A_FRANCHISE);
    assertRejected(bid(s, UUID.randomUUID(), UUID.randomUUID(), 1, 20), Reason.NOT_A_FRANCHISE);
  }

  @Test
  void aFranchiseWithPurseBelowTheAmountIsRejected() {
    var s = AuctionFixtures.lotOneOpen();

    assertRejected(bid(s, POOR, USER_POOR, 1, 20), Reason.PURSE);
  }

  @Test
  void aBidAfterOwnPassOnTheLotIsRejectedButAnotherFranchiseMayBid() {
    var s = AuctionFixtures.lotOneOpen();
    s = apply(s, pass(s, B, USER_B, 1));

    assertRejected(bid(s, B, USER_B, 1, 20), Reason.PASSED);
    assertThat(bid(s, A, USER_A, 1, 20)).isInstanceOf(Decision.Accepted.class);
  }

  @Test
  void aPassLeavesTheDeadlineAndLotUnchangedAndBarsOnlyThatLot() {
    var open = AuctionFixtures.lotOneOpen();

    var decision = pass(open, B, USER_B, 1);

    assertThat(decision).isInstanceOf(Decision.Accepted.class);
    assertThat(((Decision.Accepted) decision).events().get(0).kind()).isEqualTo(EventKind.PASSED);
    var passed = apply(open, decision);
    assertThat(passed.deadline()).isEqualTo(open.deadline());
    assertThat(passed.currentLot()).isEqualTo(open.currentLot());
    assertThat(passed.lotOpen()).isTrue();
    assertThat(passed.currentBid()).isEqualTo(open.currentBid());
    assertThat(passed.passedBy()).containsExactly(B);

    // Lot 1 closes unsold and lot 2 opens: the pass no longer applies.
    var inGap = AuctionReducer.replay(passed, TimerRules.due(passed, T0.plusSeconds(20)));
    var lotTwo = AuctionReducer.replay(inGap, TimerRules.due(inGap, T0.plusSeconds(25)));
    assertThat(lotTwo.currentLot()).isEqualTo(2);
    assertThat(lotTwo.passedBy()).isEmpty();
    assertThat(
            IntentRules.decide(
                lotTwo,
                Intent.bid(UUID.randomUUID(), USER_B, B, 2, 20),
                T0.plusSeconds(26),
                GUARDS))
        .isInstanceOf(Decision.Accepted.class);
  }

  @Test
  void aPassByANonFranchiseOrTheLeaderOrARepeatIsRejected() {
    var s = AuctionFixtures.lotOneOpen();
    assertRejected(pass(s, null, UUID.randomUUID(), 1), Reason.NOT_A_FRANCHISE);
    var led = apply(s, bid(s, A, USER_A, 1, 20));
    assertRejected(pass(led, A, USER_A, 1), Reason.LEADER);
    var passed = apply(s, pass(s, B, USER_B, 1));
    assertRejected(pass(passed, B, USER_B, 1), Reason.PASSED);
  }

  @Test
  void aBidAtOrAfterTheDeadlineIsLotClosedAndALotThatIsNotOpenIsAPhaseError() {
    var s = AuctionFixtures.lotOneOpen();

    assertRejected(
        IntentRules.decide(
            s, Intent.bid(UUID.randomUUID(), USER_A, A, 1, 20), T0.plusSeconds(20), GUARDS),
        Reason.LOT_CLOSED);
    assertThat(bid(s, A, USER_A, 2, 20)).isEqualTo(new Decision.PhaseError(Reason.NO_OPEN_LOT));
  }

  @Test
  void phaseErrorsFollowTheMatrix() {
    var none = AuctionState.initial(AuctionFixtures.ROOM, java.util.Map.of());
    assertThat(bid(none, A, USER_A, 1, 20)).isEqualTo(new Decision.PhaseError(Reason.NOT_LIVE));
    assertThat(pass(none, A, USER_A, 1)).isEqualTo(new Decision.PhaseError(Reason.NOT_LIVE));

    var open = AuctionFixtures.lotOneOpen();
    var gap = AuctionReducer.replay(open, TimerRules.due(open, T0.plusSeconds(20)));
    assertThat(bid(gap, A, USER_A, 2, 20)).isEqualTo(new Decision.PhaseError(Reason.NO_OPEN_LOT));
    assertThat(pass(gap, A, USER_A, 2)).isEqualTo(new Decision.PhaseError(Reason.NO_OPEN_LOT));

    var last = AuctionFixtures.lotOneOpen();
    for (int lot = 1; lot <= 3; lot++) {
      var s = last;
      Instant at = s.deadline();
      last = AuctionReducer.replay(s, TimerRules.due(s, at));
      if (lot < 3) {
        last = AuctionReducer.replay(last, TimerRules.due(last, last.nextOpenAt()));
      }
    }
    assertThat(last.phase().value()).isEqualTo("ended");
    assertThat(bid(last, A, USER_A, 4, 20)).isEqualTo(new Decision.PhaseError(Reason.ENDED));
  }

  @Test
  void anIntentNamingALotThatAlreadyClosedIsLotClosedInTheGapAndAfterTheEnd() {
    var open = AuctionFixtures.lotOneOpen();
    var gap = AuctionReducer.replay(open, TimerRules.due(open, T0.plusSeconds(20)));
    assertRejected(bid(gap, A, USER_A, 1, 20), Reason.LOT_CLOSED);
    assertRejected(pass(gap, A, USER_A, 1), Reason.LOT_CLOSED);

    var last = AuctionFixtures.lotOneOpen();
    for (int lot = 1; lot <= 3; lot++) {
      var s = last;
      last = AuctionReducer.replay(s, TimerRules.due(s, s.deadline()));
      if (lot < 3) {
        last = AuctionReducer.replay(last, TimerRules.due(last, last.nextOpenAt()));
      }
    }
    assertRejected(bid(last, A, USER_A, 3, 20), Reason.LOT_CLOSED);
    assertRejected(bid(last, A, USER_A, 1, 20), Reason.LOT_CLOSED);
  }
}
