package com.paddleup.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Events;
import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.Phase;
import com.paddleup.auction.model.Step;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-8, AC-14: a scripted event list rebuilds the whole auction state. */
class AuctionReducerTest {

  static final Instant T0 = Instant.parse("2030-01-01T10:00:00Z");
  static final UUID ROOM = UUID.randomUUID();
  static final UUID COMMISSIONER = UUID.randomUUID();
  static final UUID A = UUID.randomUUID();
  static final UUID B = UUID.randomUUID();
  static final UUID P1 = UUID.randomUUID();
  static final UUID P2 = UUID.randomUUID();
  static final UUID P3 = UUID.randomUUID();

  static Map<UUID, Lot> catalog() {
    var catalog = new LinkedHashMap<UUID, Lot>();
    int n = 0;
    for (UUID id : List.of(P1, P2, P3)) {
      n++;
      catalog.put(id, new Lot(0, id, "Player " + n, "Chile", "r1", List.of("t"), "S", n, 20));
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
        Map.of(A, 1000L, B, 1000L),
        List.of(P1, P2, P3));
  }

  @Test
  void aScriptedAuctionRebuildsLotPriceLeaderPursesSquadsUnsoldAndLastSeq() {
    List<AuctionEvent> log = new ArrayList<>();
    log.add(Events.scheduled(1, T0.minusSeconds(60), COMMISSIONER, T0));
    log.add(started(2));
    log.add(Events.lotOpened(3, T0, 1, T0.plusSeconds(20)));
    log.add(
        Events.bidAccepted(
            4,
            T0.plusSeconds(3),
            1,
            A,
            UUID.randomUUID(),
            UUID.randomUUID(),
            20,
            T0.plusSeconds(13)));
    log.add(Events.passed(5, T0.plusSeconds(4), 1, B, UUID.randomUUID(), UUID.randomUUID()));
    AuctionState s = AuctionReducer.replay(AuctionState.initial(ROOM, catalog()), log);

    assertThat(s.phase()).isEqualTo(Phase.LIVE);
    assertThat(s.currentLot()).isEqualTo(1);
    assertThat(s.lotOpen()).isTrue();
    assertThat(s.currentBid()).isEqualTo(20);
    assertThat(s.leader()).isEqualTo(A);
    assertThat(s.deadline()).isEqualTo(T0.plusSeconds(13));
    assertThat(s.passedBy()).containsExactly(B);
    assertThat(s.lot().name()).isEqualTo("Player 1");
    assertThat(s.lots()).extracting(Lot::no).containsExactly(1, 2, 3);
    assertThat(s.lastSeq()).isEqualTo(5);

    log.add(Events.lotSold(6, T0.plusSeconds(13), 1, A, 20));
    log.add(Events.lotOpened(7, T0.plusSeconds(18), 2, T0.plusSeconds(38)));
    log.add(Events.lotUnsold(8, T0.plusSeconds(38), 2));
    log.add(Events.lotOpened(9, T0.plusSeconds(43), 3, T0.plusSeconds(63)));
    log.add(
        Events.bidAccepted(
            10,
            T0.plusSeconds(44),
            3,
            B,
            UUID.randomUUID(),
            UUID.randomUUID(),
            20,
            T0.plusSeconds(54)));
    log.add(
        Events.bidAccepted(
            11,
            T0.plusSeconds(45),
            3,
            A,
            UUID.randomUUID(),
            UUID.randomUUID(),
            25,
            T0.plusSeconds(55)));
    log.add(Events.lotSold(12, T0.plusSeconds(55), 3, A, 25));
    log.add(Events.auctionEnded(13, T0.plusSeconds(55)));
    AuctionState end = AuctionReducer.replay(AuctionState.initial(ROOM, catalog()), log);

    assertThat(end.phase()).isEqualTo(Phase.ENDED);
    assertThat(end.purses()).containsEntry(A, 955L).containsEntry(B, 1000L);
    assertThat(end.squads().get(A)).extracting(e -> e.lot()).containsExactly(1, 3);
    assertThat(end.squads().get(A)).extracting(e -> e.price()).containsExactly(20L, 25L);
    assertThat(end.squads().get(B)).isEmpty();
    assertThat(end.unsold()).containsExactly(2);
    assertThat(end.lastResult().sold()).isTrue();
    assertThat(end.lastResult().franchiseId()).isEqualTo(A);
    assertThat(end.lotOpen()).isFalse();
    assertThat(end.nextOpenAt()).isNull();
    assertThat(end.lastSeq()).isEqualTo(13);
  }

  @Test
  void aClosedLotWaitsTheGapBeforeTheNextOne() {
    AuctionState s =
        AuctionReducer.replay(
            AuctionState.initial(ROOM, catalog()),
            List.of(
                started(1),
                Events.lotOpened(2, T0, 1, T0.plusSeconds(20)),
                Events.lotUnsold(3, T0.plusSeconds(20), 1)));

    assertThat(s.lotOpen()).isFalse();
    assertThat(s.nextOpenAt()).isEqualTo(T0.plusSeconds(25));
    assertThat(s.nextLot().name()).isEqualTo("Player 2");
    assertThat(s.currentBid()).isZero();
    assertThat(s.leader()).isNull();
    assertThat(s.lastResult().sold()).isFalse();
  }

  @Test
  void aStartRefusalIsKeptUntilARescheduleClearsIt() {
    var empty = AuctionState.initial(ROOM, Map.of());
    AuctionState refused =
        AuctionReducer.replay(
            empty,
            List.of(
                Events.scheduled(1, T0, COMMISSIONER, T0.plusSeconds(60)),
                Events.startRefused(2, T0.plusSeconds(60), "The pool is too small")));

    assertThat(refused.phase()).isEqualTo(Phase.SCHEDULED);
    assertThat(refused.startRefusal()).isEqualTo("The pool is too small");

    AuctionState again =
        AuctionReducer.apply(
            refused,
            Events.rescheduled(
                3, T0.plusSeconds(61), COMMISSIONER, T0.plusSeconds(60), T0.plusSeconds(600)));

    assertThat(again.startRefusal()).isNull();
    assertThat(again.startsAt()).isEqualTo(T0.plusSeconds(600));
    assertThat(again.phase()).isEqualTo(Phase.SCHEDULED);
  }

  @Test
  void aResumedLotGetsTheNewDeadline() {
    AuctionState s =
        AuctionReducer.replay(
            AuctionState.initial(ROOM, catalog()),
            List.of(
                started(1),
                Events.lotOpened(2, T0, 1, T0.plusSeconds(20)),
                Events.lotResumed(3, T0.plusSeconds(500), 1, T0.plusSeconds(510))));

    assertThat(s.lotOpen()).isTrue();
    assertThat(s.deadline()).isEqualTo(T0.plusSeconds(510));
  }

  @Test
  void anEventThatSkipsOrRepeatsASeqIsRefused() {
    var empty = AuctionState.initial(ROOM, Map.of());
    assertThatThrownBy(() -> AuctionReducer.apply(empty, Events.startRefused(2, T0, "x")))
        .isInstanceOf(IllegalStateException.class);
    var once = AuctionReducer.apply(empty, Events.startRefused(1, T0, "x"));
    assertThatThrownBy(() -> AuctionReducer.apply(once, Events.startRefused(1, T0, "x")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void replayingThroughJsonKeepsTheSameState() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var original = started(1);
    @SuppressWarnings("unchecked")
    Map<String, Object> viaJson =
        mapper.readValue(mapper.writeValueAsString(original.payload()), Map.class);
    var copy = new AuctionEvent(1, T0, original.kind(), null, null, null, null, viaJson);

    assertThat(AuctionReducer.apply(AuctionState.initial(ROOM, catalog()), copy))
        .isEqualTo(AuctionReducer.apply(AuctionState.initial(ROOM, catalog()), original));
  }
}
