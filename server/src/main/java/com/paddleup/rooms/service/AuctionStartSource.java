package com.paddleup.rooms.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Where the home hub reads each room's auction start. Rooms owns the port; the auction module
 * implements it, so rooms never depends on auction.
 */
public interface AuctionStartSource {

  /** Start time per room; a room with no auction start is absent from the map. */
  Map<UUID, Instant> startsAt(Collection<UUID> roomIds);
}
