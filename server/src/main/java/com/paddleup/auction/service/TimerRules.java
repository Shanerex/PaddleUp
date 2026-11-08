package com.paddleup.auction.service;

import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Events;
import com.paddleup.auction.model.Phase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * What the clock alone makes happen, as a pure function of the state and the server's time: a lot
 * closes at its deadline, the next one opens after the gap. Go-live needs the room and is not here.
 */
public final class TimerRules {

  private TimerRules() {}

  /**
   * The events due at {@code now}, empty when nothing is. One call covers one step; the caller
   * applies them and asks again until nothing is due.
   */
  public static List<AuctionEvent> due(AuctionState s, Instant now) {
    if (s.phase() != Phase.LIVE) {
      return List.of();
    }
    long seq = s.lastSeq() + 1;
    if (s.lotOpen() && s.deadline() != null && now.compareTo(s.deadline()) >= 0) {
      var events = new ArrayList<AuctionEvent>();
      events.add(
          s.leader() != null
              ? Events.lotSold(seq++, now, s.currentLot(), s.leader(), s.currentBid())
              : Events.lotUnsold(seq++, now, s.currentLot()));
      if (!s.hasMoreLots()) {
        events.add(Events.auctionEnded(seq, now));
      }
      return events;
    }
    if (!s.lotOpen()
        && s.nextOpenAt() != null
        && now.compareTo(s.nextOpenAt()) >= 0
        && s.hasMoreLots()) {
      return List.of(
          Events.lotOpened(
              seq, now, s.currentLot() + 1, now.plus(Duration.ofSeconds(s.openSeconds()))));
    }
    return List.of();
  }

  /**
   * The resume event for a lot whose deadline passed while the server was down, with a fresh T_bid
   * from now; empty when the lot is still within its deadline or no lot is open.
   */
  public static List<AuctionEvent> resume(AuctionState s, Instant now) {
    if (s.phase() == Phase.LIVE
        && s.lotOpen()
        && s.deadline() != null
        && now.compareTo(s.deadline()) >= 0) {
      return List.of(
          Events.lotResumed(
              s.lastSeq() + 1, now, s.currentLot(), now.plus(Duration.ofSeconds(s.bidSeconds()))));
    }
    return List.of();
  }
}
