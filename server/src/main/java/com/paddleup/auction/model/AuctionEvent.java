package com.paddleup.auction.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One row of the append-only log. The payload holds JSON-friendly values only: text, numbers, lists
 * and maps, with instants as ISO-8601 text.
 */
public record AuctionEvent(
    long seq,
    Instant at,
    EventKind kind,
    UUID franchiseId,
    Integer lot,
    UUID actorUserId,
    UUID intentId,
    Map<String, Object> payload) {

  public AuctionEvent {
    payload = payload == null ? Map.of() : Map.copyOf(payload);
  }

  public Instant instant(String key) {
    return Instant.parse(String.valueOf(payload.get(key)));
  }

  public long number(String key) {
    return ((Number) payload.get(key)).longValue();
  }

  public String text(String key) {
    Object value = payload.get(key);
    return value == null ? null : String.valueOf(value);
  }
}
