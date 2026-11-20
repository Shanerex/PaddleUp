package com.paddleup.auction.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds the events of the log, so each kind's payload keys live in one place. */
public final class Events {

  private Events() {}

  public static AuctionEvent scheduled(long seq, Instant at, UUID actor, Instant startsAt) {
    return new AuctionEvent(
        seq,
        at,
        EventKind.SCHEDULED,
        null,
        null,
        actor,
        null,
        Map.of("startsAt", startsAt.toString()));
  }

  public static AuctionEvent rescheduled(
      long seq, Instant at, UUID actor, Instant from, Instant to) {
    return new AuctionEvent(
        seq,
        at,
        EventKind.RESCHEDULED,
        null,
        null,
        actor,
        null,
        Map.of("from", from.toString(), "to", to.toString()));
  }

  public static AuctionEvent startRefused(long seq, Instant at, String reason) {
    return new AuctionEvent(
        seq, at, EventKind.START_REFUSED, null, null, null, null, Map.of("reason", reason));
  }

  /**
   * The snapshot the live auction works from; {@code lotOrder} holds pool player ids. {@code squad}
   * may be null, which logs no squad rules (as logs were written before the squad guard).
   */
  public static AuctionEvent auctionStarted(
      long seq,
      Instant at,
      List<Step> ladder,
      int openSeconds,
      int bidSeconds,
      int gapSeconds,
      Map<UUID, Long> purses,
      List<UUID> lotOrder,
      SquadRules squad) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put(
        "ladder",
        ladder.stream()
            .map(s -> Map.<String, Object>of("from", s.from(), "step", s.step()))
            .toList());
    payload.put("openSeconds", openSeconds);
    payload.put("bidSeconds", bidSeconds);
    payload.put("gapSeconds", gapSeconds);
    var byFranchise = new LinkedHashMap<String, Object>();
    purses.forEach((id, purse) -> byFranchise.put(id.toString(), purse));
    payload.put("purses", byFranchise);
    payload.put("lots", lotOrder.stream().map(UUID::toString).toList());
    if (squad != null) {
      var rules = new LinkedHashMap<String, Object>();
      rules.put("min", squad.min());
      rules.put("max", squad.max());
      rules.put("roleMinimums", new LinkedHashMap<String, Object>(squad.roleMinimums()));
      rules.put(
          "quotas",
          squad.quotas().stream()
              .map(
                  q ->
                      Map.<String, Object>of(
                          "category", q.category(), "tag", q.tag(), "max", q.max()))
              .toList());
      payload.put("squad", rules);
    }
    return new AuctionEvent(seq, at, EventKind.AUCTION_STARTED, null, null, null, null, payload);
  }

  public static AuctionEvent lotOpened(long seq, Instant at, int lot, Instant deadline) {
    return new AuctionEvent(
        seq,
        at,
        EventKind.LOT_OPENED,
        null,
        lot,
        null,
        null,
        Map.of("deadline", deadline.toString()));
  }

  public static AuctionEvent lotResumed(long seq, Instant at, int lot, Instant deadline) {
    return new AuctionEvent(
        seq,
        at,
        EventKind.LOT_RESUMED,
        null,
        lot,
        null,
        null,
        Map.of("deadline", deadline.toString()));
  }

  public static AuctionEvent bidAccepted(
      long seq,
      Instant at,
      int lot,
      UUID franchise,
      UUID actor,
      UUID intentId,
      long amount,
      Instant deadline) {
    return new AuctionEvent(
        seq,
        at,
        EventKind.BID_ACCEPTED,
        franchise,
        lot,
        actor,
        intentId,
        Map.of("amount", amount, "deadline", deadline.toString()));
  }

  public static AuctionEvent passed(
      long seq, Instant at, int lot, UUID franchise, UUID actor, UUID intentId) {
    return new AuctionEvent(seq, at, EventKind.PASSED, franchise, lot, actor, intentId, Map.of());
  }

  public static AuctionEvent lotSold(long seq, Instant at, int lot, UUID franchise, long amount) {
    return new AuctionEvent(
        seq, at, EventKind.LOT_SOLD, franchise, lot, null, null, Map.of("amount", amount));
  }

  public static AuctionEvent lotUnsold(long seq, Instant at, int lot) {
    return new AuctionEvent(seq, at, EventKind.LOT_UNSOLD, null, lot, null, null, Map.of());
  }

  public static AuctionEvent auctionEnded(long seq, Instant at) {
    return new AuctionEvent(seq, at, EventKind.AUCTION_ENDED, null, null, null, null, Map.of());
  }

  /** The Commissioner ended the auction from main-done or by closing an empty accelerated round. */
  public static AuctionEvent auctionEnded(long seq, Instant at, UUID actor, UUID intentId) {
    return new AuctionEvent(
        seq, at, EventKind.AUCTION_ENDED, null, null, actor, intentId, Map.of());
  }

  public static AuctionEvent startNow(long seq, Instant at, UUID actor, UUID intentId) {
    return new AuctionEvent(seq, at, EventKind.START_NOW, null, null, actor, intentId, Map.of());
  }

  /**
   * {@code heldMs} is the time frozen by the pause. On an open lot, {@code lot} is that lot and
   * {@code heldMs} its remaining time. In a gap there is no lot clock: {@code lot} is 0 and the
   * payload says {@code gap}, and {@code heldMs} is the rest of the gap.
   */
  public static AuctionEvent paused(
      long seq, Instant at, UUID actor, UUID intentId, int lot, long heldMs) {
    return new AuctionEvent(
        seq,
        at,
        EventKind.PAUSED,
        null,
        lot > 0 ? lot : null,
        actor,
        intentId,
        lot > 0 ? Map.of("heldMs", heldMs) : Map.of("heldMs", heldMs, "gap", true));
  }

  /** Resumes a pause (actor set) or ends the resume lobby on its own (actor null). */
  public static AuctionEvent resumed(long seq, Instant at, UUID actor, UUID intentId) {
    return new AuctionEvent(seq, at, EventKind.RESUMED, null, null, actor, intentId, Map.of());
  }

  public static AuctionEvent lotSkipped(long seq, Instant at, int lot, UUID actor, UUID intentId) {
    return new AuctionEvent(seq, at, EventKind.LOT_SKIPPED, null, lot, actor, intentId, Map.of());
  }

  /**
   * Closed lot {@code of} is played again as the new lot {@code newLot}. {@code franchise} and
   * {@code amount} describe the sale being reversed (franchise null when it went unsold).
   */
  public static AuctionEvent lotReopened(
      long seq,
      Instant at,
      int of,
      int newLot,
      String reason,
      UUID franchise,
      long amount,
      UUID actor,
      UUID intentId) {
    return new AuctionEvent(
        seq,
        at,
        EventKind.LOT_REOPENED,
        franchise,
        of,
        actor,
        intentId,
        Map.of("newLot", newLot, "reason", reason, "amount", amount));
  }

  public static AuctionEvent adjourned(
      long seq, Instant at, UUID actor, UUID intentId, Instant resumeAt, long heldMs) {
    return new AuctionEvent(
        seq,
        at,
        EventKind.ADJOURNED,
        null,
        null,
        actor,
        intentId,
        Map.of("resumeAt", resumeAt.toString(), "heldMs", heldMs));
  }

  /** {@code from} is null when there was no stored time to change. */
  public static AuctionEvent resumeTimeChanged(
      long seq, Instant at, UUID actor, UUID intentId, Instant from, Instant to) {
    var payload = new LinkedHashMap<String, Object>();
    if (from != null) {
      payload.put("from", from.toString());
    }
    payload.put("to", to.toString());
    return new AuctionEvent(
        seq, at, EventKind.RESUME_TIME_CHANGED, null, null, actor, intentId, payload);
  }

  /** Opens the resume lobby on Resume now (actor set) or at the stored time (actor null). */
  public static AuctionEvent resumeLobbyOpened(
      long seq, Instant at, UUID actor, UUID intentId, Instant liveAt) {
    return new AuctionEvent(
        seq,
        at,
        EventKind.RESUME_LOBBY_OPENED,
        null,
        null,
        actor,
        intentId,
        Map.of("liveAt", liveAt.toString()));
  }

  /** The main pool is done; {@code pool} holds the unsold lot numbers. */
  public static AuctionEvent mainDone(long seq, Instant at, List<Integer> pool) {
    return new AuctionEvent(
        seq, at, EventKind.MAIN_DONE, null, null, null, null, Map.of("pool", List.copyOf(pool)));
  }

  public static AuctionEvent nominatingOpened(long seq, Instant at, UUID actor, UUID intentId) {
    return new AuctionEvent(
        seq, at, EventKind.NOMINATING_OPENED, null, null, actor, intentId, Map.of());
  }

  public static AuctionEvent picksSubmitted(
      long seq, Instant at, UUID franchise, UUID actor, UUID intentId, List<Integer> lots) {
    return new AuctionEvent(
        seq,
        at,
        EventKind.PICKS_SUBMITTED,
        franchise,
        null,
        actor,
        intentId,
        Map.of("lots", List.copyOf(lots)));
  }

  /**
   * The accelerated round plays {@code order}: original unsold lot numbers, first to play first.
   */
  public static AuctionEvent roundStarted(
      long seq, Instant at, UUID actor, UUID intentId, List<Integer> order) {
    return new AuctionEvent(
        seq,
        at,
        EventKind.ROUND_STARTED,
        null,
        null,
        actor,
        intentId,
        Map.of("order", List.copyOf(order)));
  }
}
