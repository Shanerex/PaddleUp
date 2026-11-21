package com.paddleup.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.auction.AbstractAuctionIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** AC-20: owners come from the auction log's squads; unsold players and early rooms have none. */
class AuctionResultsPortIT extends AbstractAuctionIT {

  @Autowired AuctionResultsPort port;

  private UUID lotPlayer(Room room, int lot) {
    return jdbc.queryForObject(
        "SELECT pool_player_id FROM auction_lots WHERE room_id = ? AND lot_no = ?",
        UUID.class,
        UUID.fromString(room.id()),
        lot);
  }

  @Test
  void aRoomBeforeGoLiveHasNoOwners() {
    Room room = room(2, 4);

    assertThat(port.owners(UUID.fromString(room.id()))).isEmpty();
  }

  @Test
  void aSoldPlayerIsOwnedByTheWinnerAndAnUnsoldOneByNobody() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(1), 1, 20);
    advance(10);
    tick();
    advance(5);
    tick();
    advance(20);
    tick();

    var owners = port.owners(UUID.fromString(room.id()));

    assertThat(owners).containsEntry(lotPlayer(room, 1), room.franchise(1));
    assertThat(owners).doesNotContainKey(lotPlayer(room, 2));
    assertThat(owners).hasSize(1);
  }
}
