package com.paddleup.auction.service;

import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Events;
import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.Step;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A small live auction for rule tests: three franchises, three lots at base price 20. */
final class AuctionFixtures {

  static final Instant T0 = Instant.parse("2030-01-01T10:00:00Z");
  static final UUID ROOM = UUID.randomUUID();
  static final UUID A = UUID.randomUUID();
  static final UUID B = UUID.randomUUID();
  static final UUID POOR = UUID.randomUUID();
  static final UUID USER_A = UUID.randomUUID();
  static final UUID USER_B = UUID.randomUUID();
  static final UUID USER_POOR = UUID.randomUUID();
  static final List<UUID> PLAYERS =
      List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
  static final List<BidGuard> GUARDS = List.of(new PurseGuard());

  /** What the lobby lasts in these tests; production reads paddleup.auction.resume-lobby. */
  static final Duration RESUME_LOBBY = Duration.ofMinutes(2);

  /** What the clock makes happen at {@code now}, with the test resume lobby length. */
  static List<AuctionEvent> due(AuctionState s, Instant now) {
    return TimerRules.due(s, now, RESUME_LOBBY);
  }

  private AuctionFixtures() {}

  static Map<UUID, Lot> catalog() {
    var catalog = new LinkedHashMap<UUID, Lot>();
    for (int i = 0; i < PLAYERS.size(); i++) {
      UUID id = PLAYERS.get(i);
      catalog.put(id, new Lot(0, id, "Player " + (i + 1), "Chile", "r1", List.of(), "S", i, 20));
    }
    return catalog;
  }

  static AuctionEvent started(long seq) {
    return Events.auctionStarted(
        seq,
        T0,
        List.of(new Step(0, 5), new Step(100, 10)),
        20,
        10,
        5,
        Map.of(A, 1000L, B, 1000L, POOR, 10L),
        PLAYERS,
        null);
  }

  /** Lot 1 open at T0 with deadline T0 + 20 s; seq 2 is the last event. */
  static AuctionState lotOneOpen() {
    return AuctionReducer.replay(
        AuctionState.initial(ROOM, catalog()),
        List.of(started(1), Events.lotOpened(2, T0, 1, T0.plusSeconds(20))));
  }
}
