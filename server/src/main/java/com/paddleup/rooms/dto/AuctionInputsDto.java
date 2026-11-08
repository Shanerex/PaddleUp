package com.paddleup.rooms.dto;

import java.util.List;
import java.util.UUID;

/**
 * Everything the auction freezes at go-live: the room's rules, its franchises, the set order and
 * every pool player in play order (set order, then position inside the set).
 */
public record AuctionInputsDto(
    RoomConfigDto config, List<Franchise> franchises, List<String> setOrder, List<Player> players) {

  /** A franchise that takes part. */
  public record Franchise(UUID id, String name) {}

  /** One pool player as the auction shows it. */
  public record Player(
      UUID id,
      String name,
      String country,
      String role,
      List<String> tags,
      long basePrice,
      String set,
      int position) {}
}
