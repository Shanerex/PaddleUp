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

  /** A go-live snapshot with no squad rules, as logs were written before the squad guard. */
  public static AuctionEvent auctionStarted(
      long seq,
      Instant at,
      List<Step> ladder,
      int openSeconds,
      int bidSeconds,
      int gapSeconds,
      Map<UUID, Long> purses,
      List<UUID> lotOrder) {
    return auctionStarted(
        seq, at, ladder, openSeconds, bidSeconds, gapSeconds, purses, lotOrder, null);
  }

  /**
   * The snapshot the live auction works from; {@code lotOrder} holds pool player ids. {@code squad}
   * may be null, which logs no squad rules.
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
}
