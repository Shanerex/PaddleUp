package com.paddleup.auction.service;

import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Decision;
import com.paddleup.auction.model.Events;
import com.paddleup.auction.model.Intent;
import com.paddleup.auction.model.Phase;
import com.paddleup.auction.model.Reason;
import com.paddleup.auction.util.Ladder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What a bid or a pass means, as a pure function of the state, the intent and the server's clock.
 * It never reads a client clock, never touches the database and writes nothing: the runner stores
 * the events it returns. A rejection writes no event.
 */
public final class IntentRules {

  private IntentRules() {}

  public static Decision decide(AuctionState s, Intent intent, Instant now, List<BidGuard> guards) {
    if (namesClosedLot(s, intent)) {
      return new Decision.Rejected(Reason.LOT_CLOSED);
    }
    Optional<Decision> phase = phase(s);
    if (phase.isPresent()) {
      return phase.get();
    }
    if (intent.lot() < s.currentLot() || now.compareTo(s.deadline()) >= 0) {
      return new Decision.Rejected(Reason.LOT_CLOSED);
    }
    if (intent.lot() > s.currentLot()) {
      return new Decision.PhaseError(Reason.NO_OPEN_LOT);
    }
    if (intent.franchiseId() == null || !s.purses().containsKey(intent.franchiseId())) {
      return new Decision.Rejected(Reason.NOT_A_FRANCHISE);
    }
    if (s.passedBy().contains(intent.franchiseId())) {
      return new Decision.Rejected(Reason.PASSED);
    }
    if (intent.franchiseId().equals(s.leader())) {
      return new Decision.Rejected(Reason.LEADER);
    }
    return switch (intent.kind()) {
      case BID -> bid(s, intent, now, guards);
      case PASS ->
          new Decision.Accepted(
              List.of(
                  Events.passed(
                      s.lastSeq() + 1,
                      now,
                      intent.lot(),
                      intent.franchiseId(),
                      intent.actor(),
                      intent.intentId())));
    };
  }

  /**
   * True when the intent names a lot that has already closed while the auction is live (in the gap)
   * or ended. It is too late for that lot, whether or not the tick has happened yet, so the answer
   * is the same either way; only a lot that never opened is a phase error.
   */
  private static boolean namesClosedLot(AuctionState s, Intent intent) {
    return (s.phase() == Phase.LIVE || s.phase() == Phase.ENDED)
        && !s.lotOpen()
        && intent.lot() <= s.currentLot();
  }

  private static Optional<Decision> phase(AuctionState s) {
    return switch (s.phase()) {
      case NONE, SCHEDULED -> Optional.of(new Decision.PhaseError(Reason.NOT_LIVE));
      case ENDED -> Optional.of(new Decision.PhaseError(Reason.ENDED));
      case LIVE ->
          s.lotOpen() ? Optional.empty() : Optional.of(new Decision.PhaseError(Reason.NO_OPEN_LOT));
    };
  }

  private static Decision bid(AuctionState s, Intent intent, Instant now, List<BidGuard> guards) {
    long required = Ladder.required(s.ladder(), s.lot().basePrice(), s.currentBid());
    if (intent.amount() != required) {
      return new Decision.Rejected(Reason.OUTBID);
    }
    for (BidGuard guard : guards) {
      Optional<String> refusal = guard.check(s, intent.franchiseId(), intent.amount());
      if (refusal.isPresent()) {
        return new Decision.Rejected(refusal.get());
      }
    }
    AuctionEvent accepted =
        Events.bidAccepted(
            s.lastSeq() + 1,
            now,
            intent.lot(),
            intent.franchiseId(),
            intent.actor(),
            intent.intentId(),
            intent.amount(),
            now.plus(Duration.ofSeconds(s.bidSeconds())));
    return new Decision.Accepted(List.of(accepted));
  }
}
