package com.paddleup.auction.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Which franchises have the auction open: a franchise is connected while any of its STOMP sessions
 * is subscribed to the room's topic. Kept in memory, sent on the presence topic, never logged and
 * without a seq.
 */
@Component
public class Presence {

  private record Key(String session, String subscription) {}

  private record Seat(UUID roomId, UUID franchiseId) {}

  private final Map<Key, Seat> seats = new ConcurrentHashMap<>();
  private final AuctionBroadcaster broadcaster;
  private final Readiness readiness;

  Presence(AuctionBroadcaster broadcaster, Readiness readiness) {
    this.broadcaster = broadcaster;
    this.readiness = readiness;
  }

  /** The franchises connected to {@code roomId} now. */
  public Set<UUID> connected(UUID roomId) {
    var out = new LinkedHashSet<UUID>();
    seats.values().stream()
        .filter(s -> s.roomId().equals(roomId))
        .map(Seat::franchiseId)
        .sorted()
        .forEach(out::add);
    return Collections.unmodifiableSet(out);
  }

  /**
   * A franchise's session subscribed to the room. A Commissioner without a franchise is not
   * counted.
   */
  public void connect(String session, String subscription, UUID roomId, UUID franchiseId) {
    if (franchiseId == null) {
      return;
    }
    seats.put(new Key(session, subscription), new Seat(roomId, franchiseId));
    broadcaster.presence(roomId, connected(roomId), readiness.ready(roomId));
  }

  /** One subscription ended. */
  public void leave(String session, String subscription) {
    Seat seat = seats.remove(new Key(session, subscription));
    if (seat != null) {
      broadcaster.presence(seat.roomId(), connected(seat.roomId()), readiness.ready(seat.roomId()));
    }
  }

  /** A session closed: all its subscriptions end. */
  public void leaveAll(String session) {
    Set<UUID> rooms = new LinkedHashSet<>();
    seats
        .entrySet()
        .removeIf(
            e -> {
              boolean mine = e.getKey().session().equals(session);
              if (mine) {
                rooms.add(e.getValue().roomId());
              }
              return mine;
            });
    rooms.forEach(room -> broadcaster.presence(room, connected(room), readiness.ready(room)));
  }
}
