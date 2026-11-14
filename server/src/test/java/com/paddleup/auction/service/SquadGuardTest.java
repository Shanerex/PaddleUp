package com.paddleup.auction.service;

import static com.paddleup.auction.service.SquadScenario.A;
import static com.paddleup.auction.service.SquadScenario.B;
import static com.paddleup.auction.service.SquadScenario.card;
import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.BidLock;
import com.paddleup.auction.model.Reason;
import com.paddleup.auction.model.SquadRules;
import com.paddleup.auction.service.SquadScenario.Card;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-1, AC-2: the squad maximum and the restricted-category caps. */
class SquadGuardTest {

  private final SquadGuard guard = new SquadGuard();

  private static final SquadRules RULES =
      new SquadRules(2, 3, Map.of(), List.of(new SquadRules.Quota("cap", "x", 1)));

  private static AuctionState state(List<Card> pool, UUID... wins) {
    return SquadScenario.build(RULES, 1000, pool, List.of(wins));
  }

  @Test
  void aFranchiseHoldingSquadMaxCannotBid() {
    AuctionState s =
        state(
            List.of(
                card("r1", 10),
                card("r1", 10),
                card("r1", 10),
                card("r1", 10),
                card("r1", 10),
                card("r1", 10)),
            A,
            A,
            A);

    assertThat(guard.check(s, A, 10)).contains(Reason.SQUAD_FULL);
    assertThat(guard.check(s, B, 10)).isEmpty();
  }

  @Test
  void oneBelowSquadMaxMayStillBid() {
    AuctionState s = state(List.of(card("r1", 10), card("r1", 10), card("r1", 10)), A, A);

    assertThat(guard.check(s, A, 10)).isEmpty();
  }

  @Test
  void aTaggedLotIsRefusedWhenTheFranchiseIsAtThatCategoryCap() {
    AuctionState s = state(List.of(card("r1", 10, "x"), card("r1", 10, "x"), card("r1", 10)), A);

    assertThat(guard.check(s, A, 10)).contains(Reason.QUOTA);
    assertThat(guard.limit(s, A)).contains(BidLock.any(Reason.QUOTA, null, "cap", 1));
    assertThat(guard.check(s, B, 10)).isEmpty();
  }

  @Test
  void anUntaggedLotIsNotAffectedByAFullCategory() {
    AuctionState s = state(List.of(card("r1", 10, "x"), card("r1", 10), card("r1", 10)), A);

    assertThat(guard.check(s, A, 10)).isEmpty();
  }

  @Test
  void belowTheCategoryCapATaggedLotMayBeBid() {
    AuctionState s = state(List.of(card("r1", 10), card("r1", 10, "x"), card("r1", 10)), A);

    assertThat(guard.check(s, A, 10)).isEmpty();
  }

  @Test
  void aStateWithNoSquadRulesHasNoSquadGuard() {
    AuctionState s = SquadScenario.build(null, 1000, List.of(card("r1", 10)), List.of());

    assertThat(guard.check(s, A, 10)).isEqualTo(Optional.empty());
    assertThat(guard.limit(s, A)).isEmpty();
  }

  // Reserve: the squad min is 4, role r2 needs 1, so a win leaves 3 more to buy.
  private static final SquadRules RESERVE_RULES = new SquadRules(4, 6, Map.of("r2", 1), List.of());

  /** Lot 1 (open, r1, base 10), then r2 at 30, r1 at 5, r1 at 7, r1 at 100: reserve is 30+5+7. */
  private static AuctionState open(long purse) {
    return SquadScenario.build(
        RESERVE_RULES,
        purse,
        List.of(card("r1", 10), card("r2", 30), card("r1", 5), card("r1", 7), card("r1", 100)),
        List.of());
  }

  @Test
  void aBidThatLeavesExactlyTheReserveIsAllowed() {
    assertThat(guard.check(open(52), A, 10)).isEmpty();
  }

  @Test
  void aBidOneUnitUnderTheReserveIsRefusedWithTheRoleBehindIt() {
    AuctionState s = open(51);

    assertThat(guard.check(s, A, 10)).contains(Reason.RESERVE);
    // reserve 42 of a purse of 51: bids up to 9 pass
    assertThat(guard.limit(s, A)).contains(new BidLock(Reason.RESERVE, "r2", null, null, 9L));
    assertThat(guard.check(s, A, 9)).isEmpty();
  }

  @Test
  void heldPlayersLowerTheReserve() {
    // A already holds lot 1 (r1) and lot 2 (r2): lot 3 is open, 1 more to reach the min of 4.
    AuctionState s =
        SquadScenario.build(
            RESERVE_RULES,
            100,
            List.of(card("r1", 10), card("r2", 30), card("r1", 5), card("r1", 7), card("r1", 100)),
            List.of(A, A));

    // reserve after winning lot 3 at 5: the cheapest remaining is 7; 100 - 60 - 5 = 35 left
    assertThat(guard.check(s, A, 5)).isEmpty();
    // B holds nothing and lot 2 (the only r2) went to A, so r2 cannot be filled; after lot 3 it
    // still needs 2 more of the rest (7 + 100): 107 > 95 left
    assertThat(guard.check(s, B, 5)).contains(Reason.RESERVE);
  }

  @Test
  void aSquadAlreadyLegalHasNoSquadLimit() {
    // A holds r1 and r2 after lots 1 and 2, then lots 3 and 4: the squad min of 4 is met.
    AuctionState s =
        SquadScenario.build(
            RESERVE_RULES,
            100,
            List.of(card("r1", 10), card("r2", 30), card("r1", 5), card("r1", 7), card("r1", 9)),
            List.of(A, A, A, A));

    assertThat(guard.limit(s, A)).isEmpty();
  }

  @Test
  void aLotThatTakesTheLastPlaceANeededRoleMustUseIsBlockedAtAnyPrice() {
    // Squad max 2 and r2 needs 1: A holds one r1, so winning r1 lot 2 leaves no place for r2.
    var rules = new SquadRules(2, 2, Map.of("r2", 1), List.of());
    AuctionState s =
        SquadScenario.build(
            rules, 1000, List.of(card("r1", 1), card("r1", 1), card("r2", 1)), List.of(A));

    assertThat(guard.limit(s, A)).contains(BidLock.any(Reason.NO_ROOM, "r2", null, null));
    assertThat(guard.check(s, A, 1)).contains(Reason.NO_ROOM);
  }

  @Test
  void aPoolThatCannotFillTheSquadStillLetsAFranchiseBidTowardIt() {
    // Squad min 4, 2 lots in all: no squad can reach 4, but winning lot 1 gets closer.
    var rules = new SquadRules(4, 6, Map.of(), List.of());
    AuctionState s =
        SquadScenario.build(rules, 100, List.of(card("r1", 10), card("r1", 20)), List.of());

    // the reserve is the one lot left (20), so a bid up to 80 passes
    assertThat(guard.limit(s, A)).contains(new BidLock(Reason.RESERVE, null, null, null, 80L));
    assertThat(guard.check(s, A, 10)).isEmpty();
  }
}
