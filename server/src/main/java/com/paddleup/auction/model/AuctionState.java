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
 * <p>{@code currentLot} is 0 until lot 1 opens. {@code lotOpen} is false in a gap, when {@code
 * nextOpenAt} says when the next lot opens, and {@code lastResult} says how the last one closed.
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
    Map<UUID, Lot> catalog,
    List<Lot> lots,
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
    long lastSeq) {

  public AuctionState {
    ladder = List.copyOf(ladder);
    catalog = Collections.unmodifiableMap(new LinkedHashMap<>(catalog));
    lots = List.copyOf(lots);
    passedBy = Collections.unmodifiableSet(new LinkedHashSet<>(passedBy));
    purses = Collections.unmodifiableMap(new LinkedHashMap<>(purses));
    var copy = new LinkedHashMap<UUID, List<SquadEntry>>();
    squads.forEach((id, entries) -> copy.put(id, List.copyOf(entries)));
    squads = Collections.unmodifiableMap(copy);
    unsold = List.copyOf(unsold);
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
        catalog,
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
        0);
  }

  /** The lot being auctioned now, or the one that just closed; null before lot 1 opens. */
  public Lot lot() {
    return currentLot >= 1 && currentLot <= lots.size() ? lots.get(currentLot - 1) : null;
  }

  /** The lot that opens after the gap; null unless the auction is in a gap with a lot to come. */
  public Lot nextLot() {
    boolean inGap = phase == Phase.LIVE && !lotOpen && nextOpenAt != null;
    return inGap && currentLot < lots.size() ? lots.get(currentLot) : null;
  }

  public boolean hasMoreLots() {
    return currentLot < lots.size();
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
    public Map<UUID, Lot> catalog;
    public List<Lot> lots;
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

    private Builder(AuctionState s) {
      roomId = s.roomId;
      phase = s.phase;
      startsAt = s.startsAt;
      startRefusal = s.startRefusal;
      ladder = s.ladder;
      openSeconds = s.openSeconds;
      bidSeconds = s.bidSeconds;
      gapSeconds = s.gapSeconds;
      catalog = s.catalog;
      lots = s.lots;
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
          catalog,
          lots,
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
          lastSeq);
    }
  }
}
