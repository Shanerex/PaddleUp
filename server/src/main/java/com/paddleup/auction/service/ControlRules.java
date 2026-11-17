package com.paddleup.auction.service;

import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Control;
import com.paddleup.auction.model.Decision;
import com.paddleup.auction.model.Events;
import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.Phase;
import com.paddleup.auction.model.Picks;
import com.paddleup.auction.model.Reason;
import com.paddleup.auction.model.SquadEntry;
import com.paddleup.auction.util.ReasonText;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a Commissioner control or an accelerated-round pick means, as a pure function of the state,
 * the request and the server's clock, following the state x entry-point matrix. It trusts nothing
 * from the client but the intent itself, writes nothing and never reads a clock: the runner stores
 * the events it returns. A refusal writes no event. Whether the caller is the Commissioner is
 * checked before the request gets here.
 */
public final class ControlRules {

  private ControlRules() {}

  public static Decision decide(AuctionState s, Control c, Instant now, Duration resumeLobby) {
    return switch (c.action()) {
      case PAUSE -> pause(s, c, now);
      case RESUME -> resume(s, c, now);
      case SKIP -> skip(s, c, now);
      case REOPEN -> reopen(s, c, now);
      case ADJOURN -> adjourn(s, c, now);
      case CHANGE_TIME -> changeTime(s, c, now);
      case RESUME_NOW -> resumeNow(s, c, now, resumeLobby);
      case START_NOW -> startNow(s, c, now);
      case OPEN_ROUND -> openRound(s, c, now);
      case END_AUCTION -> endAuction(s, c, now);
      case END_ROUND -> endRound(s, c, now);
    };
  }

  private static Decision accept(AuctionEvent... events) {
    return new Decision.Accepted(List.of(events));
  }

  private static Decision reject(String reason) {
    return new Decision.Rejected(reason);
  }

  /** The time the open lot has left, or the rest of the gap, in whole milliseconds. */
  private static long remainingMs(AuctionState s, Instant now) {
    Instant at = s.lotOpen() ? s.deadline() : s.nextOpenAt();
    return at == null ? 0 : Math.max(0, Duration.between(now, at).toMillis());
  }

  private static Decision pause(AuctionState s, Control c, Instant now) {
    if (s.phase() != Phase.LIVE) {
      return reject(Reason.WRONG_PHASE);
    }
    return accept(
        Events.paused(
            s.lastSeq() + 1, now, c.actor(), c.intentId(), s.currentLot(), remainingMs(s, now)));
  }

  private static Decision resume(AuctionState s, Control c, Instant now) {
    if (s.phase() != Phase.PAUSED) {
      return reject(Reason.WRONG_PHASE);
    }
    return accept(Events.resumed(s.lastSeq() + 1, now, c.actor(), c.intentId()));
  }

  private static Decision skip(AuctionState s, Control c, Instant now) {
    if (s.phase() != Phase.LIVE) {
      return reject(Reason.WRONG_PHASE);
    }
    if (!s.lotOpen()) {
      return reject(Reason.NO_OPEN_LOT);
    }
    if (c.lot() != 0 && c.lot() != s.currentLot()) {
      return reject(Reason.LOT_CLOSED);
    }
    if (s.leader() != null) {
      return reject(Reason.HAS_BID);
    }
    return accept(Events.lotSkipped(s.lastSeq() + 1, now, s.currentLot(), c.actor(), c.intentId()));
  }

  private static Decision reopen(AuctionState s, Control c, Instant now) {
    if (s.phase() != Phase.LIVE) {
      return reject(Reason.WRONG_PHASE);
    }
    if (ReasonText.tooLong(c.reason())) {
      return reject(Reason.REASON_TOO_LONG);
    }
    String reason = ReasonText.sanitise(c.reason());
    if (reason.isEmpty()) {
      return reject(Reason.REASON_REQUIRED);
    }
    int of = c.lot();
    boolean played = of >= 1 && of <= s.lots().size() && !s.queue().contains(of);
    if (!played || (s.lotOpen() && of == s.currentLot())) {
      return reject(Reason.NOT_CLOSED);
    }
    Lot lot = s.lots().get(of - 1);
    // Only the latest play of a player can be re-opened; a later lot of it is queued, open or
    // closed.
    if (s.lots().stream().anyMatch(l -> l.no() > of && l.playerId().equals(lot.playerId()))) {
      return reject(Reason.SUPERSEDED);
    }
    UUID buyer = null;
    long price = 0;
    for (var squad : s.squads().entrySet()) {
      for (SquadEntry entry : squad.getValue()) {
        if (entry.lot() == of) {
          buyer = squad.getKey();
          price = entry.price();
        }
      }
    }
    if (buyer == null && !s.unsold().contains(of)) {
      return reject(Reason.NOT_CLOSED);
    }
    return accept(
        Events.lotReopened(
            s.lastSeq() + 1,
            now,
            of,
            s.lots().size() + 1,
            reason,
            buyer,
            price,
            c.actor(),
            c.intentId()));
  }

  private static boolean future(Control c, Instant now) {
    return c.resumeAt() != null && c.resumeAt().isAfter(now);
  }

  private static Decision adjourn(AuctionState s, Control c, Instant now) {
    if (s.phase() != Phase.LIVE && s.phase() != Phase.PAUSED) {
      return reject(Reason.WRONG_PHASE);
    }
    if (!future(c, now)) {
      return reject(Reason.TIME_INVALID);
    }
    long held = s.phase() == Phase.PAUSED ? s.heldMs() : remainingMs(s, now);
    return accept(
        Events.adjourned(s.lastSeq() + 1, now, c.actor(), c.intentId(), c.resumeAt(), held));
  }

  private static Decision changeTime(AuctionState s, Control c, Instant now) {
    if (s.phase() != Phase.ADJOURNED && s.phase() != Phase.RESUME_LOBBY) {
      return reject(Reason.WRONG_PHASE);
    }
    if (!future(c, now)) {
      return reject(Reason.TIME_INVALID);
    }
    return accept(
        Events.resumeTimeChanged(
            s.lastSeq() + 1, now, c.actor(), c.intentId(), s.resumeAt(), c.resumeAt()));
  }

  private static Decision resumeNow(AuctionState s, Control c, Instant now, Duration lobby) {
    if (s.phase() != Phase.ADJOURNED) {
      return reject(Reason.WRONG_PHASE);
    }
    return accept(
        Events.resumeLobbyOpened(s.lastSeq() + 1, now, c.actor(), c.intentId(), now.plus(lobby)));
  }

  /**
   * Start now. In the lobby the runner goes live after logging {@code start_now}; in the resume
   * lobby the lot goes live at once, in the same step.
   */
  private static Decision startNow(AuctionState s, Control c, Instant now) {
    long seq = s.lastSeq() + 1;
    return switch (s.phase()) {
      case SCHEDULED -> accept(Events.startNow(seq, now, c.actor(), c.intentId()));
      case RESUME_LOBBY ->
          accept(
              Events.startNow(seq, now, c.actor(), c.intentId()),
              Events.resumed(seq + 1, now, null, null));
      default -> reject(Reason.WRONG_PHASE);
    };
  }

  private static Decision openRound(AuctionState s, Control c, Instant now) {
    if (s.phase() != Phase.MAIN_DONE) {
      return reject(Reason.WRONG_PHASE);
    }
    if (!AuctionState.MAIN.equals(s.round())) {
      return reject(Reason.ROUND_DONE);
    }
    return accept(Events.nominatingOpened(s.lastSeq() + 1, now, c.actor(), c.intentId()));
  }

  private static Decision endAuction(AuctionState s, Control c, Instant now) {
    if (s.phase() != Phase.MAIN_DONE) {
      return reject(Reason.WRONG_PHASE);
    }
    return accept(Events.auctionEnded(s.lastSeq() + 1, now, c.actor(), c.intentId()));
  }

  /**
   * End round: picking closes and the picked lots are played, most-picked first, ties by lot
   * number. With no picks the auction ends.
   */
  private static Decision endRound(AuctionState s, Control c, Instant now) {
    if (s.phase() != Phase.NOMINATING) {
      return reject(Reason.WRONG_PHASE);
    }
    List<Integer> order = order(s);
    long seq = s.lastSeq() + 1;
    if (order.isEmpty()) {
      return accept(Events.auctionEnded(seq, now, c.actor(), c.intentId()));
    }
    return accept(
        Events.roundStarted(seq, now, c.actor(), c.intentId(), order),
        Events.lotOpened(
            seq + 1, now, s.lots().size() + 1, now.plus(Duration.ofSeconds(s.openSeconds()))));
  }

  /** How many franchises picked each unsold lot; lots nobody picked are absent. */
  public static Map<Integer, Integer> pickCounts(AuctionState s) {
    Map<Integer, Integer> counts = new LinkedHashMap<>();
    for (List<Integer> lots : s.picks().values()) {
      for (int lot : lots) {
        if (s.unsold().contains(lot)) {
          counts.merge(lot, 1, Integer::sum);
        }
      }
    }
    return counts;
  }

  /** The picked lots in the order the accelerated round plays them. */
  static List<Integer> order(AuctionState s) {
    Map<Integer, Integer> counts = pickCounts(s);
    List<Integer> order = new ArrayList<>(counts.keySet());
    order.sort(
        Comparator.<Integer>comparingInt(counts::get)
            .reversed()
            .thenComparingInt(Integer::intValue));
    return order;
  }

  /**
   * How many unsold lots a franchise may still pick: its squad's open places, never more than the
   * pool, and the pool itself when the room has no squad rules.
   */
  public static int openSlots(AuctionState s, UUID franchiseId) {
    int pool = s.unsold().size();
    if (s.squadRules() == null) {
      return pool;
    }
    long held = s.squads().getOrDefault(franchiseId, List.of()).size();
    return (int) Math.max(0, Math.min(pool, (long) s.squadRules().max() - held));
  }

  /** A franchise's nominations; it replaces the ones it sent before. */
  public static Decision decidePicks(AuctionState s, Picks p, Instant now) {
    if (s.phase() != Phase.NOMINATING) {
      return reject(Reason.WRONG_PHASE);
    }
    if (p.franchiseId() == null || !s.purses().containsKey(p.franchiseId())) {
      return reject(Reason.NOT_A_FRANCHISE);
    }
    Set<Integer> distinct = new HashSet<>(p.lots());
    if (distinct.size() != p.lots().size() || !s.unsold().containsAll(distinct)) {
      return reject(Reason.NOT_UNSOLD);
    }
    if (p.lots().size() > openSlots(s, p.franchiseId())) {
      return reject(Reason.OVER_SLOTS);
    }
    return accept(
        Events.picksSubmitted(
            s.lastSeq() + 1, now, p.franchiseId(), p.actor(), p.intentId(), p.lots()));
  }
}
