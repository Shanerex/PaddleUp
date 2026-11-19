package com.paddleup.auction.service;

import com.paddleup.auction.model.AccelSlot;
import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.LastResult;
import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.Phase;
import com.paddleup.auction.model.SquadEntry;
import com.paddleup.auction.model.SquadRules;
import com.paddleup.auction.model.Step;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
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
        b.queue.remove(Integer.valueOf(e.lot()));
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
      case START_NOW -> {
        if (b.phase == Phase.SCHEDULED) {
          b.startsAt = e.at();
          // A fresh go-live request: an earlier refusal must not stop the tick retrying it.
          b.startRefusal = null;
        }
      }
      case PAUSED -> hold(b, Phase.PAUSED, e.number("heldMs"));
      case RESUMED -> resumed(b, e);
      case LOT_SKIPPED -> {
        b.unsold.add(e.lot());
        b.lastResult = new LastResult(e.lot(), null, 0);
        closed(b, e);
      }
      case LOT_REOPENED -> reopened(b, e);
      case ADJOURNED -> {
        hold(b, Phase.ADJOURNED, e.number("heldMs"));
        b.resumeAt = e.instant("resumeAt");
      }
      case RESUME_TIME_CHANGED -> {
        b.phase = Phase.ADJOURNED;
        b.resumeAt = e.instant("to");
        b.liveAt = null;
      }
      case RESUME_LOBBY_OPENED -> {
        b.phase = Phase.RESUME_LOBBY;
        b.liveAt = e.instant("liveAt");
      }
      case MAIN_DONE -> {
        b.phase = Phase.MAIN_DONE;
        b.nextOpenAt = null;
      }
      case NOMINATING_OPENED -> {
        b.phase = Phase.NOMINATING;
        b.round = AuctionState.ACCELERATED;
        b.picks = new LinkedHashMap<>();
      }
      case PICKS_SUBMITTED -> b.picks.put(e.franchiseId(), ints(e.payload().get("lots")));
      case ROUND_STARTED -> roundStarted(b, e);
      case LIFELINE_SPENT, LIFELINE_REFUNDED -> {}
    }
    return b.build();
  }

  /**
   * Freezes the open lot's timer (or the gap) as {@code heldMs}; the clock reads null meanwhile.
   */
  private static void hold(AuctionState.Builder b, Phase phase, long heldMs) {
    b.phase = phase;
    b.heldMs = heldMs;
    b.deadline = null;
    b.nextOpenAt = null;
    b.liveAt = null;
  }

  /** Back to live with exactly the time that was frozen. */
  private static void resumed(AuctionState.Builder b, AuctionEvent e) {
    Instant at = e.at().plusMillis(b.heldMs);
    if (b.lotOpen) {
      b.deadline = at;
    } else {
      b.nextOpenAt = at;
    }
    b.phase = Phase.LIVE;
    b.heldMs = 0;
    b.resumeAt = null;
    b.liveAt = null;
  }

  /**
   * Closed lot {@code of} comes back as a new lot at the front of the queue, from the same card at
   * base price. The sale (purse, squad) or the unsold entry is reversed.
   */
  private static void reopened(AuctionState.Builder b, AuctionEvent e) {
    int of = e.lot();
    b.lotsClosed--;
    if (e.franchiseId() != null) {
      b.purses.merge(e.franchiseId(), e.number("amount"), Long::sum);
      List<SquadEntry> squad = new ArrayList<>(b.squads.getOrDefault(e.franchiseId(), List.of()));
      squad.removeIf(x -> x.lot() == of);
      b.squads.put(e.franchiseId(), squad);
    } else {
      b.unsold.remove(Integer.valueOf(of));
    }
    if (b.lastResult != null && b.lastResult.lot() == of) {
      b.lastResult = null;
    }
    List<Lot> lots = new ArrayList<>(b.lots);
    int newLot = again(lots, of);
    b.lots = lots;
    b.lotsByPrice = byPrice(lots);
    if (newLot != (int) e.number("newLot")) {
      throw new IllegalStateException("Re-opened lot " + of + " is not numbered " + newLot);
    }
    if (b.accel.containsKey(of)) {
      b.accel.put(newLot, b.accel.get(of));
    }
    b.queue.addFirst(newLot);
  }

  /** The accelerated round: the picked unsold lots, in order, each as a new lot. */
  private static void roundStarted(AuctionState.Builder b, AuctionEvent e) {
    SequencedSet<Integer> queue = new LinkedHashSet<>();
    b.accel = new LinkedHashMap<>();
    List<Lot> lots = new ArrayList<>(b.lots);
    for (int original : ints(e.payload().get("order"))) {
      int picks = (int) b.picks.values().stream().filter(l -> l.contains(original)).count();
      b.unsold.remove(Integer.valueOf(original));
      int no = again(lots, original);
      queue.add(no);
      b.accel.put(no, new AccelSlot(queue.size(), picks));
    }
    b.lots = lots;
    b.lotsByPrice = byPrice(lots);
    b.accelSize = queue.size();
    b.queue = queue;
    b.phase = Phase.LIVE;
    b.lotOpen = false;
    b.nextOpenAt = null;
    clearLot(b);
  }

  /**
   * Appends to {@code lots} a new lot built from the card of lot {@code original}; returns its
   * number. The caller sorts {@code lotsByPrice} once, after the last lot it adds.
   */
  private static int again(List<Lot> lots, int original) {
    Lot card = lots.get(original - 1);
    int no = lots.size() + 1;
    lots.add(
        new Lot(
            no,
            card.playerId(),
            card.name(),
            card.country(),
            card.role(),
            card.tags(),
            card.set(),
            card.position(),
            card.basePrice()));
    return no;
  }

  private static List<Lot> byPrice(List<Lot> lots) {
    return lots.stream()
        .sorted(Comparator.comparingLong(Lot::basePrice).thenComparingInt(Lot::no))
        .toList();
  }

  private static List<Integer> ints(Object value) {
    List<Integer> out = new ArrayList<>();
    if (value instanceof List<?> list) {
      for (Object item : list) {
        out.add(((Number) item).intValue());
      }
    }
    return out;
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
    b.squadRules = squadRules((Map<String, Object>) e.payload().get("squad"));
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
    b.lotsByPrice = byPrice(lots);
    b.currentLot = 0;
    b.lotOpen = false;
    b.queue = new LinkedHashSet<>(lots.stream().map(Lot::no).toList());
    b.unsold = new ArrayList<>();
    b.nextOpenAt = null;
    b.lastResult = null;
    b.round = AuctionState.MAIN;
    b.heldMs = 0;
    b.resumeAt = null;
    b.liveAt = null;
    b.picks = new LinkedHashMap<>();
    b.accel = new LinkedHashMap<>();
    b.accelSize = 0;
    clearLot(b);
  }

  /**
   * The frozen squad rules, or null for a go-live logged before the squad guard. Read defensively:
   * an entry that is not a number is skipped, so one bad value can never stop a room's replay.
   */
  private static SquadRules squadRules(Map<String, Object> squad) {
    if (squad == null) {
      return null;
    }
    Map<String, Integer> roleMinimums = new TreeMap<>();
    if (squad.get("roleMinimums") instanceof Map<?, ?> mins) {
      mins.forEach(
          (role, min) -> {
            if (role != null && min instanceof Number n) {
              roleMinimums.put(String.valueOf(role), n.intValue());
            }
          });
    }
    List<SquadRules.Quota> quotas = new ArrayList<>();
    if (squad.get("quotas") instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> q && q.get("max") instanceof Number max) {
          quotas.add(
              new SquadRules.Quota(
                  String.valueOf(q.get("category")), String.valueOf(q.get("tag")), max.intValue()));
        }
      }
    }
    return new SquadRules(
        size(squad.get("min"), 0), size(squad.get("max"), Integer.MAX_VALUE), roleMinimums, quotas);
  }

  /** A squad size from the log, or {@code missing} (no limit that way) when it is not a number. */
  private static int size(Object value, int missing) {
    return value instanceof Number n ? n.intValue() : missing;
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
    b.lotsClosed++;
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
