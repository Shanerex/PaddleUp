package com.paddleup.auction.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the server knows about one auction, rebuilt by folding its event log. Immutable: the
 * reducer returns a new state per event. {@code catalog} holds the lot cards (by pool player id)
 * that the log refers to by id; it is read from {@code auction_lots} and is not part of the log.
 *
 * <p>{@code squadRules} is frozen at go-live; null for a log written before the squad guard, which
 * means no squad guard. {@code currentLot} is 0 until lot 1 opens. {@code lotOpen} is false in a
 * gap, when {@code nextOpenAt} says when the next lot opens, and {@code lastResult} says how the
 * last one closed. {@code lotsByPrice} holds the same lots by base price, then lot number, sorted
 * once at go-live so the squad reserve never sorts on the bid path.
 *
 * <p>{@code queue} holds the lot numbers still to be played, in play order; the lot after the
 * current one is its head. A re-opened or accelerated lot is a new lot number (the next one, built
 * from the original card) put in the queue, so a lot number in the log never changes meaning.
 * {@code heldMs} is the time frozen by pause or adjourn (the open lot's remaining time, or the rest
 * of the gap), {@code resumeAt} the adjourn time, {@code liveAt} the end of the resume lobby.
 * {@code round} is {@code main} or {@code accelerated}; {@code picks} are the accelerated-round
 * nominations by franchise, in lot numbers. {@code accel} holds, by lot number, the position and
 * pick count of each lot the accelerated round plays (fixed when the round starts; a re-opened lot
 * keeps those of the lot it replays) and {@code accelSize} how many lots the round has.
 */
public record AuctionState(
    UUID roomId,
    Phase phase,
    Instant startsAt,
    String startRefusal,
    List<Step> ladder,
    int openSeconds,
    int bidSeconds,
    int gapSeconds,
    SquadRules squadRules,
    Map<UUID, Lot> catalog,
    List<Lot> lots,
    List<Lot> lotsByPrice,
    int currentLot,
    boolean lotOpen,
    long currentBid,
    UUID leader,
    Instant deadline,
    Set<UUID> passedBy,
    Map<UUID, Long> purses,
    Map<UUID, List<SquadEntry>> squads,
    List<Integer> unsold,
    Instant nextOpenAt,
    LastResult lastResult,
    long lastSeq,
    List<Integer> queue,
    long heldMs,
    Instant resumeAt,
    Instant liveAt,
    String round,
    Map<UUID, List<Integer>> picks,
    Map<Integer, AccelSlot> accel,
    int accelSize) {

  public static final String MAIN = "main";
  public static final String ACCELERATED = "accelerated";

  public AuctionState {
    ladder = List.copyOf(ladder);
    catalog = Collections.unmodifiableMap(new LinkedHashMap<>(catalog));
    lots = List.copyOf(lots);
    lotsByPrice = List.copyOf(lotsByPrice);
    passedBy = Collections.unmodifiableSet(new LinkedHashSet<>(passedBy));
    purses = Collections.unmodifiableMap(new LinkedHashMap<>(purses));
    var copy = new LinkedHashMap<UUID, List<SquadEntry>>();
    squads.forEach((id, entries) -> copy.put(id, List.copyOf(entries)));
    squads = Collections.unmodifiableMap(copy);
    unsold = List.copyOf(unsold);
    queue = List.copyOf(queue);
    var pickCopy = new LinkedHashMap<UUID, List<Integer>>();
    picks.forEach((id, nos) -> pickCopy.put(id, List.copyOf(nos)));
    picks = Collections.unmodifiableMap(pickCopy);
    accel = Collections.unmodifiableMap(new LinkedHashMap<>(accel));
  }

  /** A room with no auction yet. {@code catalog} may be empty until the lots are frozen. */
  public static AuctionState initial(UUID roomId, Map<UUID, Lot> catalog) {
    return new AuctionState(
        roomId,
        Phase.NONE,
        null,
        null,
        List.of(),
        0,
        0,
        0,
        null,
        catalog,
        List.of(),
        List.of(),
        0,
        false,
        0,
        null,
        null,
        Set.of(),
        Map.of(),
        Map.of(),
        List.of(),
        null,
        null,
        0,
        List.of(),
        0,
        null,
        null,
        MAIN,
        Map.of(),
        Map.of(),
        0);
  }

  /** The lot being auctioned now, or the one that just closed; null before lot 1 opens. */
  public Lot lot() {
    return currentLot >= 1 && currentLot <= lots.size() ? lots.get(currentLot - 1) : null;
  }

  /**
   * The lot that opens after the gap; null unless the auction is in a gap (held or not) with a lot
   * to come.
   */
  public Lot nextLot() {
    boolean held = phase == Phase.PAUSED || phase == Phase.ADJOURNED || phase == Phase.RESUME_LOBBY;
    boolean inGap = (phase == Phase.LIVE || held) && !lotOpen && currentLot >= 1;
    return inGap && !queue.isEmpty() ? lots.get(queue.get(0) - 1) : null;
  }

  public boolean hasMoreLots() {
    return !queue.isEmpty();
  }

  public Builder edit() {
    return new Builder(this);
  }

  /** A mutable copy used by the reducer to change a few fields at once. */
  public static final class Builder {
    public UUID roomId;
    public Phase phase;
    public Instant startsAt;
    public String startRefusal;
    public List<Step> ladder;
    public int openSeconds;
    public int bidSeconds;
    public int gapSeconds;
    public SquadRules squadRules;
    public Map<UUID, Lot> catalog;
    public List<Lot> lots;
    public List<Lot> lotsByPrice;
    public int currentLot;
    public boolean lotOpen;
    public long currentBid;
    public UUID leader;
    public Instant deadline;
    public Set<UUID> passedBy;
    public Map<UUID, Long> purses;
    public Map<UUID, List<SquadEntry>> squads;
    public List<Integer> unsold;
    public Instant nextOpenAt;
    public LastResult lastResult;
    public long lastSeq;
    public List<Integer> queue;
    public long heldMs;
    public Instant resumeAt;
    public Instant liveAt;
    public String round;
    public Map<UUID, List<Integer>> picks;
    public Map<Integer, AccelSlot> accel;
    public int accelSize;

    private Builder(AuctionState s) {
      roomId = s.roomId;
      phase = s.phase;
      startsAt = s.startsAt;
      startRefusal = s.startRefusal;
      ladder = s.ladder;
      openSeconds = s.openSeconds;
      bidSeconds = s.bidSeconds;
      gapSeconds = s.gapSeconds;
      squadRules = s.squadRules;
      catalog = s.catalog;
      lots = s.lots;
      lotsByPrice = s.lotsByPrice;
      currentLot = s.currentLot;
      lotOpen = s.lotOpen;
      currentBid = s.currentBid;
      leader = s.leader;
      deadline = s.deadline;
      passedBy = new LinkedHashSet<>(s.passedBy);
      purses = new LinkedHashMap<>(s.purses);
      squads = new LinkedHashMap<>(s.squads);
      unsold = new ArrayList<>(s.unsold);
      nextOpenAt = s.nextOpenAt;
      lastResult = s.lastResult;
      lastSeq = s.lastSeq;
      queue = new ArrayList<>(s.queue);
      heldMs = s.heldMs;
      resumeAt = s.resumeAt;
      liveAt = s.liveAt;
      round = s.round;
      picks = new LinkedHashMap<>(s.picks);
      accel = new LinkedHashMap<>(s.accel);
      accelSize = s.accelSize;
    }

    public AuctionState build() {
      return new AuctionState(
          roomId,
          phase,
          startsAt,
          startRefusal,
          ladder,
          openSeconds,
          bidSeconds,
          gapSeconds,
          squadRules,
          catalog,
          lots,
          lotsByPrice,
          currentLot,
          lotOpen,
          currentBid,
          leader,
          deadline,
          passedBy,
          purses,
          squads,
          unsold,
          nextOpenAt,
          lastResult,
          lastSeq,
          queue,
          heldMs,
          resumeAt,
          liveAt,
          round,
          picks,
          accel,
          accelSize);
    }
  }
}
