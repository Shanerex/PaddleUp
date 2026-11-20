package com.paddleup.auction.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.SquadRules;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** AC-3: the reserve is the cheapest legal set of base prices that fills the unmet minimums. */
class SquadReserveTest {

  private static int next = 1;

  private static Lot lot(String role, long base, String... tags) {
    int no = next++;
    return new Lot(no, UUID.randomUUID(), "P" + no, "Chile", role, List.of(tags), "S", no, base);
  }

  /** The reserve with {@code remaining} handed over in base-price order, as the state keeps it. */
  private static SquadReserve.Result of(
      SquadRules rules, List<Lot> held, Lot open, List<Lot> remaining) {
    var byPrice =
        remaining.stream()
            .sorted(Comparator.comparingLong(Lot::basePrice).thenComparingInt(Lot::no))
            .toList();
    return SquadReserve.of(
        rules, held, open, byPrice, remaining.stream().map(Lot::no).collect(Collectors.toSet()));
  }

  private static SquadRules rules(int min, Map<String, Integer> roles, SquadRules.Quota... quotas) {
    return new SquadRules(min, 10, roles, List.of(quotas));
  }

  @Test
  void unmetRoleMinimumsAreFilledWithTheCheapestPlayersOfThatRole() {
    var rules = rules(2, Map.of("a", 2));
    var open = lot("b", 50);

    var result =
        of(rules, List.of(), open, List.of(lot("a", 30), lot("a", 10), lot("a", 20), lot("b", 5)));

    assertThat(result.cost()).isEqualTo(30);
    assertThat(result.role()).isEqualTo("a");
  }

  @Test
  void theLotsOwnRoleCountsAsAlreadyWon() {
    var rules = rules(2, Map.of("a", 2));
    var open = lot("a", 50);

    var result = of(rules, List.of(), open, List.of(lot("a", 30), lot("a", 10)));

    assertThat(result.cost()).isEqualTo(10);
  }

  @Test
  void heldPlayersCountTowardTheRoleMinimum() {
    var rules = rules(2, Map.of("a", 2));

    var result = of(rules, List.of(lot("a", 1)), lot("b", 50), List.of(lot("a", 30)));

    assertThat(result.cost()).isEqualTo(30);
  }

  @Test
  void theRestOfTheSquadMinimumIsFilledWithTheCheapestOfAnyRole() {
    var rules = rules(4, Map.of("a", 1));
    var open = lot("b", 50);

    var result =
        of(rules, List.of(), open, List.of(lot("a", 40), lot("b", 5), lot("c", 7), lot("c", 9)));

    // a: 40, then 3 - 1 = 2 more at the cheapest of the rest: 5 + 7
    assertThat(result.cost()).isEqualTo(40 + 5 + 7);
    assertThat(result.role()).isEqualTo("a");
  }

  @Test
  void anAlreadyLegalSquadNeedsNoReserve() {
    var rules = rules(2, Map.of("a", 1));

    var result = of(rules, List.of(lot("a", 1)), lot("b", 5), List.of(lot("a", 99)));

    assertThat(result.cost()).isZero();
    assertThat(result.role()).isNull();
  }

  @Test
  void aFillerThatBreaksACategoryCapIsSkipped() {
    var cap = new SquadRules.Quota("cap", "x", 1);
    var rules = rules(3, Map.of(), cap);
    var open = lot("a", 50, "x");

    var result =
        of(
            rules,
            List.of(),
            open,
            List.of(lot("a", 1, "x"), lot("a", 6), lot("a", 8, "x"), lot("a", 9)));

    // the open lot already uses the one place, so the cheap tagged ones are skipped
    assertThat(result.cost()).isEqualTo(6 + 9);
  }

  @Test
  void aRoleThePoolLacksIsLeftShortButTheRestStillCounts() {
    // a needs 2 and the pool has one a: the reserve covers what the pool can supply.
    var rules = rules(2, Map.of("a", 2));

    var result = of(rules, List.of(), lot("b", 5), List.of(lot("a", 1)));

    assertThat(result.blocked()).isFalse();
    assertThat(result.cost()).isEqualTo(1);
    assertThat(result.role()).isEqualTo("a");
  }

  @Test
  void aRoleWithNoneLeftDoesNotStopABidOnAnotherRole() {
    // a needs 1 and no a is left: winning b neither helps nor hurts a, so it is allowed at base.
    var rules = rules(1, Map.of("a", 1));

    var result = of(rules, List.of(), lot("b", 5), List.of(lot("b", 3)));

    assertThat(result.blocked()).isFalse();
    assertThat(result.cost()).isZero();
  }

  @Test
  void aPoolTooSmallForTheSquadMinimumStillCountsWhatItHas() {
    // min 11, 6 held, 3 left after the open lot: winning it gets to 10, the closest possible.
    var rules = new SquadRules(11, 15, Map.of(), List.of());
    var held =
        List.of(lot("a", 1), lot("a", 1), lot("a", 1), lot("a", 1), lot("a", 1), lot("a", 1));

    var result = of(rules, held, lot("a", 5), List.of(lot("a", 2), lot("a", 3), lot("a", 4)));

    assertThat(result.blocked()).isFalse();
    assertThat(result.cost()).isEqualTo(2 + 3 + 4);
    assertThat(result.role()).isNull();
  }

  @Test
  void winningTheLastPlaceANeededRoleMustUseIsBlocked() {
    var rules = new SquadRules(2, 2, Map.of("a", 1), List.of());

    var result = of(rules, List.of(lot("b", 1)), lot("b", 5), List.of(lot("a", 1)));

    assertThat(result.blocked()).isTrue();
    assertThat(result.role()).isEqualTo("a");
  }

  @Test
  void winningTheLastCappedPlaceANeededRoleMustUseIsBlocked() {
    // cap 1 on x, and the only a left carries x: a tagged b would take that place. Passing b,
    // the a and the c make a legal squad.
    var cap = new SquadRules.Quota("cap", "x", 1);
    var rules = rules(2, Map.of("a", 1), cap);

    var result = of(rules, List.of(), lot("b", 5, "x"), List.of(lot("a", 1, "x"), lot("c", 1)));

    assertThat(result.blocked()).isTrue();
    assertThat(result.role()).isEqualTo("a");
  }

  @Test
  void theCheapestLegalSquadIsFoundEvenWhenTheGreedyPickWouldSpendACappedPlace() {
    // cap 1 on x. r: 10 (x) or 20; s: 5 (x) only. Greedy takes r at 10 and then cannot fill s;
    // the cheapest legal squad is r at 20 and s at 5.
    var cap = new SquadRules.Quota("cap", "x", 1);
    var rules = rules(3, Map.of("r", 1, "s", 1), cap);

    var result =
        of(
            rules,
            List.of(),
            lot("t", 50),
            List.of(lot("r", 10, "x"), lot("r", 20), lot("s", 5, "x")));

    assertThat(result.blocked()).isFalse();
    assertThat(result.cost()).isEqualTo(25);
    assertThat(result.role()).isEqualTo("r");
  }

  @Test
  void aLotNumberedBeforeTheOpenOneStillCountsWhenItIsQueuedToPlayLater() {
    // A re-opened lot gets a new, higher number but a lot queued from before can be lower than
    // the open one: only the upcoming set decides what is still to come.
    var rules = rules(2, Map.of("a", 2));
    var cheapA = lot("a", 10);
    var open = lot("a", 50);

    var result = of(rules, List.of(), open, List.of(cheapA));

    assertThat(cheapA.no()).isLessThan(open.no());
    assertThat(result.cost()).isEqualTo(10);
  }

  @Test
  void aLotThatIsNotUpcomingIsNotCountedEvenWithAHigherNumber() {
    var rules = rules(2, Map.of("a", 2));
    var open = lot("a", 50);
    var played = lot("a", 5);
    var queued = lot("a", 30);
    var byPrice = List.of(played, queued);

    var result = SquadReserve.of(rules, List.of(), open, byPrice, java.util.Set.of(queued.no()));

    assertThat(played.no()).isGreaterThan(open.no());
    assertThat(result.cost()).isEqualTo(30);
  }
}
