package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.PackDto;
import com.paddleup.rooms.RoomsTestSupport;
import com.paddleup.scoring.StatLines;
import com.paddleup.scoring.dto.EvaluationDto;
import com.paddleup.scoring.model.StatLine;
import org.junit.jupiter.api.Test;

/** AC-7: the cricket default rule set against totals worked out by hand from PRD 7.4 and R-4. */
class CricketGoldenTest {

  static final PackDto PACK = RoomsTestSupport.cricket();

  static EvaluationDto score(String role, StatLine line) {
    return PointsEvaluator.evaluate(
        PACK.defaultRuleSet(), PACK.appearanceRule(), PACK.statSchema(), role, line);
  }

  /** The rule ids and points of the lines, in rule order, as "id=points" strings. */
  static void assertLines(EvaluationDto result, String... expected) {
    assertThat(
            result.lines().stream()
                .map(l -> l.ruleId() + "=" + l.points().toPlainString())
                .toList())
        .containsExactly(expected);
  }

  @Test
  void aCenturyBatter() {
    // 104 + 9 fours (18) + 6 sixes (24) + 50 bonus (10) + 100 bonus (20) + SR 189.09 (6) + XI (4)
    var result =
        score(
            "BAT",
            StatLines.of()
                .stat("runs", 104)
                .stat("balls-faced", 55)
                .stat("fours", 9)
                .stat("sixes", 6)
                .stat("strike-rate", "189.09")
                .stat("in-playing-xi", 1)
                .build());

    assertThat(result.total()).isEqualByComparingTo("186");
    assertLines(
        result,
        "runs-points=104",
        "four-bonus=18",
        "six-bonus=24",
        "fifty-bonus=10",
        "century-bonus=20",
        "strike-rate-bands=6",
        "appearance-bonus=4");
  }

  @Test
  void aFiveWicketBowler() {
    // 5 wickets (125) + bowled x2 (16) + lbw x1 (8) + 3-wicket (10) + 5-wicket (20) + maiden (12)
    // + economy 5.50 (4) + XI (4)
    var result =
        score(
            "BOWL",
            StatLines.of()
                .stat("wickets", 5)
                .event("wickets", "bowled", 2)
                .event("wickets", "lbw", 1)
                .event("wickets", "caught", 2)
                .stat("balls-bowled", 24)
                .stat("runs-conceded", 22)
                .stat("maidens", 1)
                .stat("economy", "5.50")
                .stat("in-playing-xi", 1)
                .build());

    assertThat(result.total()).isEqualByComparingTo("199");
    assertLines(
        result,
        "wicket-points=125",
        "lbw-bonus=8",
        "bowled-bonus=16",
        "three-wicket-bonus=10",
        "five-wicket-bonus=20",
        "maiden-points=12",
        "economy-bands=4",
        "appearance-bonus=4");
  }

  @Test
  void aKeeperWithAStumpingAndPlayerOfTheMatch() {
    // 12 runs + 1 four (2) + 3 catches (24) + catch tier (4) + stumping (12) + PoM (25) + XI (4);
    // 8 balls faced is under the 10-ball minimum, so no strike-rate band.
    var result =
        score(
            "WK",
            StatLines.of()
                .stat("runs", 12)
                .stat("balls-faced", 8)
                .stat("fours", 1)
                .stat("strike-rate", "150")
                .stat("catches", 3)
                .stat("stumpings", 1)
                .stat("player-of-match", 1)
                .stat("in-playing-xi", 1)
                .build());

    assertThat(result.total()).isEqualByComparingTo("83");
    assertLines(
        result,
        "runs-points=12",
        "four-bonus=2",
        "catch-points=24",
        "catch-tier-bonus=4",
        "stumping-points=12",
        "player-of-match-points=25",
        "appearance-bonus=4");
  }

  @Test
  void anAllRounderOnBothBoundaries() {
    // SR exactly 170 takes the higher band (+6) and economy exactly 10 the worse one (-2).
    // 34 runs + 3 fours (6) + 2 sixes (8) + SR (6) + 1 wicket (25) + economy (-2) + XI (4)
    var result =
        score(
            "AR",
            StatLines.of()
                .stat("runs", 34)
                .stat("balls-faced", 20)
                .stat("fours", 3)
                .stat("sixes", 2)
                .stat("strike-rate", "170.00")
                .stat("wickets", 1)
                .event("wickets", "caught", 1)
                .stat("balls-bowled", 12)
                .stat("runs-conceded", 20)
                .stat("economy", "10.00")
                .stat("in-playing-xi", 1)
                .build());

    assertThat(result.total()).isEqualByComparingTo("81");
  }

  @Test
  void aBenchedPlayerScoresNothing() {
    var result = score("BAT", StatLines.of().stat("in-playing-xi", 0).build());

    assertThat(result.total()).isEqualByComparingTo("0");
    assertThat(result.lines()).isEmpty();
  }
}
