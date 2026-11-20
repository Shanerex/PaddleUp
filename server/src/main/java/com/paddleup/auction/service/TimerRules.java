package com.paddleup.auction.service;

import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Events;
import com.paddleup.auction.model.Phase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * What the clock alone makes happen, as a pure function of the state and the server's time: a lot
 * closes at its deadline, the next one opens after the gap. Go-live needs the room and is not here.
 */
public final class TimerRules {

  private TimerRules() {}

  /**
   * The events due at {@code now}, empty when nothing is. One call covers one step; the caller
   * applies them and asks again until nothing is due. Paused, main-done and nominating never change
   * with time, and an adjourned auction only ever opens its resume lobby, never goes live.
   */
  public static List<AuctionEvent> due(AuctionState s, Instant now, Duration resumeLobby) {
    long seq = s.lastSeq() + 1;
    return switch (s.phase()) {
      case LIVE -> live(s, now, seq);
      case ADJOURNED ->
          s.resumeAt() != null && now.compareTo(s.resumeAt()) >= 0
              ? List.of(Events.resumeLobbyOpened(seq, now, null, null, now.plus(resumeLobby)))
              : List.of();
      case RESUME_LOBBY ->
          s.liveAt() != null && now.compareTo(s.liveAt()) >= 0
              ? List.of(Events.resumed(seq, now, null, null))
              : List.of();
      default -> List.of();
    };
  }

  private static List<AuctionEvent> live(AuctionState s, Instant now, long seq) {
    if (s.lotOpen() && s.deadline() != null && now.compareTo(s.deadline()) >= 0) {
      return List.of(
          s.leader() != null
              ? Events.lotSold(seq, now, s.currentLot(), s.leader(), s.currentBid())
              : Events.lotUnsold(seq, now, s.currentLot()));
    }
    if (!s.lotOpen() && s.nextOpenAt() != null && now.compareTo(s.nextOpenAt()) >= 0) {
      if (s.hasMoreLots()) {
        return List.of(
            Events.lotOpened(
                seq, now, s.queue().getFirst(), now.plus(Duration.ofSeconds(s.openSeconds()))));
      }
      // The last lot's gap has ended (R-14): lots left unsold go to the Commissioner's choice,
      // none (or an accelerated round just played) ends the auction.
      return List.of(
          AuctionState.MAIN.equals(s.round()) && !s.unsold().isEmpty()
              ? Events.mainDone(seq, now, s.unsold())
              : Events.auctionEnded(seq, now));
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
