package com.paddleup.auction.service;

import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Events;
import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.SquadRules;
import com.paddleup.auction.model.Step;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A live auction for squad-rule tests. The pool is a list of cards in play order; the first {@code
 * wins.size()} lots are sold in turn to the franchises listed, and the next lot is open.
 */
final class SquadScenario {

  static final Instant T0 = Instant.parse("2030-01-01T10:00:00Z");
  static final UUID ROOM = UUID.randomUUID();
  static final UUID A = UUID.randomUUID();
  static final UUID B = UUID.randomUUID();

  private SquadScenario() {}

  /** A pool card: its role id, tags and base price. */
  record Card(String role, List<String> tags, long base) {}

  static Card card(String role, long base, String... tags) {
    return new Card(role, List.of(tags), base);
  }

  /** {@code wins} names who takes lot 1, 2, ... at its base price; the next lot is open. */
  static AuctionState build(SquadRules rules, long purse, List<Card> pool, List<UUID> wins) {
    Map<UUID, Lot> catalog = new LinkedHashMap<>();
    List<UUID> order = new ArrayList<>();
    for (int i = 0; i < pool.size(); i++) {
      Card c = pool.get(i);
      UUID id = UUID.randomUUID();
      order.add(id);
      catalog.put(
          id, new Lot(0, id, "Player " + (i + 1), "Chile", c.role(), c.tags(), "S", i, c.base()));
    }
    List<AuctionEvent> log = new ArrayList<>();
    long seq = 1;
    log.add(
        Events.auctionStarted(
            seq++,
            T0,
            List.of(new Step(0, 5)),
            20,
            10,
            5,
            Map.of(A, purse, B, purse),
            order,
            rules));
    for (int lot = 1; lot <= wins.size(); lot++) {
      log.add(Events.lotOpened(seq++, T0, lot, T0.plusSeconds(20)));
      log.add(Events.lotSold(seq++, T0, lot, wins.get(lot - 1), pool.get(lot - 1).base()));
    }
    log.add(Events.lotOpened(seq, T0, wins.size() + 1, T0.plusSeconds(20)));
    return AuctionReducer.replay(AuctionState.initial(ROOM, catalog), log);
  }
}
