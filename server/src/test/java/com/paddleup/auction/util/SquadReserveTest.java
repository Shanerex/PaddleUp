package com.paddleup.auction.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.SquadRules;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-3: the reserve is the cheapest base prices that fill the unmet minimums. */
class SquadReserveTest {

  private static int next = 1;

  private static Lot lot(String role, long base, String... tags) {
    int no = next++;
    return new Lot(no, UUID.randomUUID(), "P" + no, "Chile", role, List.of(tags), "S", no, base);
  }

  private static SquadRules rules(int min, Map<String, Integer> roles, SquadRules.Quota... quotas) {
    return new SquadRules(min, 10, roles, List.of(quotas));
  }

  @Test
  void unmetRoleMinimumsAreFilledWithTheCheapestPlayersOfThatRole() {
    var rules = rules(2, Map.of("a", 2));
    var open = lot("b", 50);

    var result =
        SquadReserve.of(
            rules, List.of(), open, List.of(lot("a", 30), lot("a", 10), lot("a", 20), lot("b", 5)));

    assertThat(result.cost()).isEqualTo(30);
    assertThat(result.role()).isEqualTo("a");
  }

  @Test
  void theLotsOwnRoleCountsAsAlreadyWon() {
    var rules = rules(2, Map.of("a", 2));
    var open = lot("a", 50);

    var result = SquadReserve.of(rules, List.of(), open, List.of(lot("a", 30), lot("a", 10)));

    assertThat(result.cost()).isEqualTo(10);
  }

  @Test
  void heldPlayersCountTowardTheRoleMinimum() {
    var rules = rules(2, Map.of("a", 2));

    var result = SquadReserve.of(rules, List.of(lot("a", 1)), lot("b", 50), List.of(lot("a", 30)));

    assertThat(result.cost()).isEqualTo(30);
  }

  @Test
  void theRestOfTheSquadMinimumIsFilledWithTheCheapestOfAnyRole() {
    var rules = rules(4, Map.of("a", 1));
    var open = lot("b", 50);

    var result =
        SquadReserve.of(
            rules, List.of(), open, List.of(lot("a", 40), lot("b", 5), lot("c", 7), lot("c", 9)));

    // a: 40, then 3 - 1 = 2 more at the cheapest of the rest: 5 + 7
    assertThat(result.cost()).isEqualTo(40 + 5 + 7);
    assertThat(result.role()).isEqualTo("a");
  }

  @Test
  void anAlreadyLegalSquadNeedsNoReserve() {
    var rules = rules(2, Map.of("a", 1));

    var result = SquadReserve.of(rules, List.of(lot("a", 1)), lot("b", 5), List.of(lot("a", 99)));

    assertThat(result.cost()).isZero();
    assertThat(result.role()).isNull();
  }

  @Test
  void aFillerThatBreaksACategoryCapIsSkipped() {
    var cap = new SquadRules.Quota("cap", "x", 1);
    var rules = rules(3, Map.of(), cap);
    var open = lot("a", 50, "x");

    var result =
        SquadReserve.of(
            rules,
            List.of(),
            open,
            List.of(lot("a", 1, "x"), lot("a", 6), lot("a", 8, "x"), lot("a", 9)));

    // the open lot already uses the one place, so the cheap tagged ones are skipped
    assertThat(result.cost()).isEqualTo(6 + 9);
  }

  @Test
  void aPoolThatCannotSupplyTheRoleIsUnfillable() {
    var rules = rules(2, Map.of("a", 2));

    var result = SquadReserve.of(rules, List.of(), lot("b", 5), List.of(lot("a", 1)));

    assertThat(result.cost()).isEqualTo(SquadReserve.UNFILLABLE);
    assertThat(result.role()).isEqualTo("a");
  }

  @Test
  void aPoolThatCannotSupplyTheSquadMinimumIsUnfillable() {
    var rules = rules(5, Map.of());

    var result = SquadReserve.of(rules, List.of(), lot("b", 5), List.of(lot("a", 1)));

    assertThat(result.cost()).isEqualTo(SquadReserve.UNFILLABLE);
    assertThat(result.role()).isNull();
  }
}
