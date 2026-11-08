package com.paddleup.auction.model;

import java.util.List;
import java.util.UUID;

/** One player up for auction: the card frozen at go-live, numbered from 1 in play order. */
public record Lot(
    int no,
    UUID playerId,
    String name,
    String country,
    String role,
    List<String> tags,
    String set,
    int position,
    long basePrice) {

  public Lot {
    tags = List.copyOf(tags);
  }
}
