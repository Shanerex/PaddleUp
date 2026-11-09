package com.paddleup.auction.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Which franchises have said they are ready in the lobby. Kept in memory like {@link Presence},
 * never logged and without a seq: a restart clears it and managers tap again. It is cleared when
 * the auction starts.
 */
@Component
public class Readiness {

  private final ConcurrentMap<UUID, Set<UUID>> ready = new ConcurrentHashMap<>();

  /** The franchises of {@code roomId} that are ready now, in a stable order. */
  public Set<UUID> ready(UUID roomId) {
    Set<UUID> now = ready.get(roomId);
    if (now == null) {
      return Set.of();
    }
    synchronized (now) {
      return Collections.unmodifiableSet(new LinkedHashSet<>(now));
    }
  }

  /** Marks one franchise ready or not ready; true if that changed anything. */
  boolean set(UUID roomId, UUID franchiseId, boolean on) {
    Set<UUID> now =
        ready.computeIfAbsent(roomId, id -> Collections.synchronizedSet(new LinkedHashSet<>()));
    return on ? now.add(franchiseId) : now.remove(franchiseId);
  }

  /** The auction started: nobody is waiting any more. */
  void clear(UUID roomId) {
    ready.remove(roomId);
  }
}
