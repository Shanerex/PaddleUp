package com.paddleup.auction.service;

import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.LastResult;
import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.Phase;
import com.paddleup.auction.model.SquadEntry;
import com.paddleup.auction.model.Step;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The only place an event changes auction state: a pure function from (state, event) to the next
 * state. The live runner applies it after an event is stored, and boot recovery replays the whole
 * log through it, so both reach the same state.
 */
public final class AuctionReducer {

  private AuctionReducer() {}

  /** Folds {@code events} onto {@code state}, in order. */
  public static AuctionState replay(AuctionState state, List<AuctionEvent> events) {
    AuctionState s = state;
    for (AuctionEvent e : events) {
      s = apply(s, e);
    }
    return s;
  }

  /** The state after {@code e}. The event's seq must follow the state's last seq. */
  public static AuctionState apply(AuctionState state, AuctionEvent e) {
    if (e.seq() != state.lastSeq() + 1) {
      throw new IllegalStateException(
          "Event seq " + e.seq() + " does not follow " + state.lastSeq());
    }
    AuctionState.Builder b = state.edit();
    b.lastSeq = e.seq();
    switch (e.kind()) {
      case SCHEDULED -> {
        b.phase = Phase.SCHEDULED;
        b.startsAt = e.instant("startsAt");
        b.startRefusal = null;
      }
      case RESCHEDULED -> {
        b.startsAt = e.instant("to");
        b.startRefusal = null;
      }
      case START_REFUSED -> b.startRefusal = e.text("reason");
      case AUCTION_STARTED -> started(b, e);
      case LOT_OPENED -> {
        clearLot(b);
        b.currentLot = e.lot();
        b.lotOpen = true;
        b.deadline = e.instant("deadline");
        b.nextOpenAt = null;
      }
      case BID_ACCEPTED -> {
        b.currentBid = e.number("amount");
        b.leader = e.franchiseId();
        b.deadline = e.instant("deadline");
      }
      case PASSED -> b.passedBy.add(e.franchiseId());
      case LOT_SOLD -> sold(b, e);
      case LOT_UNSOLD -> {
        b.unsold.add(e.lot());
        b.lastResult = new LastResult(e.lot(), null, 0);
        closed(b, e);
      }
      case LOT_RESUMED -> b.deadline = e.instant("deadline");
      case AUCTION_ENDED -> {
        b.phase = Phase.ENDED;
        b.nextOpenAt = null;
      }
    }
    return b.build();
  }

  @SuppressWarnings("unchecked")
  private static void started(AuctionState.Builder b, AuctionEvent e) {
    b.phase = Phase.LIVE;
    b.startRefusal = null;
    b.ladder =
        ((List<Map<String, Object>>) e.payload().get("ladder"))
            .stream()
                .map(
                    m ->
                        new Step(
                            ((Number) m.get("from")).longValue(),
                            ((Number) m.get("step")).longValue()))
                .toList();
    b.openSeconds = (int) e.number("openSeconds");
    b.bidSeconds = (int) e.number("bidSeconds");
    b.gapSeconds = (int) e.number("gapSeconds");
    // Sorted by franchise id: jsonb does not keep key order, so live state and replay must agree.
    Map<UUID, Long> purses = new TreeMap<>();
    ((Map<String, Object>) e.payload().get("purses"))
        .forEach((id, purse) -> purses.put(UUID.fromString(id), ((Number) purse).longValue()));
    b.purses = purses;
    b.squads = new LinkedHashMap<>();
    purses.keySet().forEach(id -> b.squads.put(id, List.of()));
    List<Lot> lots = new ArrayList<>();
    int no = 1;
    for (Object id : (List<Object>) e.payload().get("lots")) {
      Lot card = b.catalog.get(UUID.fromString(String.valueOf(id)));
      if (card == null) {
        throw new IllegalStateException("No lot card for player " + id);
      }
      lots.add(
          new Lot(
              no++,
              card.playerId(),
              card.name(),
              card.country(),
              card.role(),
              card.tags(),
              card.set(),
              card.position(),
              card.basePrice()));
    }
    b.lots = lots;
    b.currentLot = 0;
    b.lotOpen = false;
    b.unsold = new ArrayList<>();
    b.nextOpenAt = null;
    b.lastResult = null;
    clearLot(b);
  }

  private static void sold(AuctionState.Builder b, AuctionEvent e) {
    UUID franchise = e.franchiseId();
    long price = e.number("amount");
    b.purses.merge(franchise, -price, Long::sum);
    List<SquadEntry> squad = new ArrayList<>(b.squads.getOrDefault(franchise, List.of()));
    Lot lot = b.lots.get(e.lot() - 1);
    squad.add(new SquadEntry(lot.no(), lot.playerId(), price));
    b.squads.put(franchise, squad);
    b.lastResult = new LastResult(e.lot(), franchise, price);
    closed(b, e);
  }

  /** A lot closed: no bid is live, and the next lot opens after the gap. */
  private static void closed(AuctionState.Builder b, AuctionEvent e) {
    b.lotOpen = false;
    clearLot(b);
    b.nextOpenAt = e.at().plus(Duration.ofSeconds(b.gapSeconds));
  }

  private static void clearLot(AuctionState.Builder b) {
    b.currentBid = 0;
    b.leader = null;
    b.deadline = null;
    b.passedBy = new LinkedHashSet<>();
  }
}
