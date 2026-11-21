package com.paddleup.auction.service;

import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.SquadEntry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Read-only door to the auction results for other modules. It reads the squads of the auction state
 * rebuilt from the event log, so it never writes and never shows anything the log does not hold.
 */
@Service
public class AuctionResultsPort {

  private final AuctionRegistry registry;

  AuctionResultsPort(AuctionRegistry registry) {
    this.registry = registry;
  }

  /**
   * Who owns each sold pool player: pool player id to franchise id. Empty before go-live and for a
   * room whose auction sold nothing; an unsold player has no entry.
   */
  public Map<UUID, UUID> owners(UUID roomId) {
    AuctionState state = registry.runner(roomId).state();
    Map<UUID, UUID> owners = new LinkedHashMap<>();
    state
        .squads()
        .forEach(
            (franchiseId, entries) -> {
              for (SquadEntry entry : entries) {
                owners.put(entry.playerId(), franchiseId);
              }
            });
    return owners;
  }
}
