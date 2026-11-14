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
    assertThat(guard.lock(s, A, 10)).contains(new BidLock(Reason.QUOTA, null, "cap"));
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
    assertThat(guard.lock(s, A, 10)).isEmpty();
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
    assertThat(guard.lock(s, A, 10)).contains(new BidLock(Reason.RESERVE, "r2", null));
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
    // B holds nothing, so after lot 3 it still needs r2 (30) and 2 more (7 + 100): 137 > 95 left
    assertThat(guard.check(s, B, 5)).contains(Reason.RESERVE);
  }
}
