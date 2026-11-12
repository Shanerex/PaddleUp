package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.AppearanceRuleDto;
import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.rooms.RoomsTestSupport;
import com.paddleup.scoring.StatLines;
import com.paddleup.scoring.dto.EvaluationDto;
import com.paddleup.scoring.dto.PointsLineDto;
import com.paddleup.scoring.model.StatLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** AC-3, AC-4, AC-5, AC-6, AC-8: the evaluator's six primitives, over the real pack rules. */
class PointsEvaluatorTest {

  static final PackDto PACK = RoomsTestSupport.cricket();

  static EvaluationDto eval(String role, StatLine line) {
    return PointsEvaluator.evaluate(
        PACK.defaultRuleSet(), PACK.appearanceRule(), PACK.statSchema(), role, line);
  }

  static Optional<BigDecimal> pointsOf(EvaluationDto result, String ruleId) {
    return result.lines().stream()
        .filter(l -> l.ruleId().equals(ruleId))
        .map(PointsLineDto::points)
        .findFirst();
  }

  static void assertRule(EvaluationDto result, String ruleId, String expected) {
    if (expected == null) {
      assertThat(pointsOf(result, ruleId)).as(ruleId).isEmpty();
    } else {
      assertThat(pointsOf(result, ruleId)).as(ruleId).isPresent();
      assertThat(pointsOf(result, ruleId).get()).as(ruleId).isEqualByComparingTo(expected);
    }
  }

  // ---- per-unit, appearance, line shape, labels (AC-5, AC-8) ----

  @Test
  void perUnitMultipliesTheStatByThePointsAndEmitsOneLabelledLine() {
    var result = eval("BAT", StatLines.of().stat("runs", 30).stat("fours", 3).build());

    assertThat(result.total()).isEqualByComparingTo("36");
    assertThat(result.lines())
        .containsExactly(
            new PointsLineDto("runs-points", "Runs", new BigDecimal("30")),
            new PointsLineDto("four-bonus", "Four bonus", new BigDecimal("6")));
  }

  @Test
  void aRuleThatGivesZeroPointsOrDoesNotFireEmitsNoLine() {
    var rules = new RuleSetDto(List.of(new RuleDto.PerUnit("r", "s", BigDecimal.ZERO)));
    var zero =
        PointsEvaluator.evaluate(rules, null, List.of(), "x", StatLines.of().stat("s", 5).build());
    var empty = eval("BAT", StatLine.EMPTY);

    assertThat(zero.lines()).isEmpty();
    assertThat(zero.total()).isEqualByComparingTo("0");
    assertThat(empty.lines()).isEmpty();
    assertThat(empty.total()).isEqualByComparingTo("0");
  }

  @Test
  void aPlayerInTheXiWithNoOtherStatsGetsTheAppearanceBonusAndANonXiPlayerNone() {
    var inXi = eval("BAT", StatLines.of().stat("in-playing-xi", 1).build());
    var outOfXi = eval("BAT", StatLines.of().stat("in-playing-xi", 0).build());

    assertThat(inXi.total()).isEqualByComparingTo("4");
    assertThat(inXi.lines()).extracting(PointsLineDto::ruleId).containsExactly("appearance-bonus");
    assertThat(outOfXi.total()).isEqualByComparingTo("0");
    assertThat(outOfXi.lines()).isEmpty();
  }

  @Test
  void theAppearanceThresholdIsAtLeastAndARoomWithoutOneGivesNoBonus() {
    var rules = new RuleSetDto(List.of(new RuleDto.AppearanceBonus("a", new BigDecimal("2"))));
    var rule = new AppearanceRuleDto("mins", new BigDecimal("10"));

    assertThat(eval(rules, rule, "x", StatLines.of().stat("mins", 9).build()).total())
        .isEqualByComparingTo("0");
    assertThat(eval(rules, rule, "x", StatLines.of().stat("mins", 10).build()).total())
        .isEqualByComparingTo("2");
    assertThat(eval(rules, null, "x", StatLines.of().stat("mins", 10).build()).total())
        .isEqualByComparingTo("0");
  }

  private static EvaluationDto eval(
      RuleSetDto rules, AppearanceRuleDto appearance, String role, StatLine line) {
    return PointsEvaluator.evaluate(rules, appearance, List.of(), role, line);
  }

  @Test
  void theLabelFallsBackFromTheRuleLabelToTheStatLabelToTheId() {
    var rules =
        new RuleSetDto(
            List.of(
                new RuleDto.PerUnit("own", "s1", BigDecimal.ONE, "Own label"),
                new RuleDto.PerUnit("stat", "s1", BigDecimal.ONE),
                new RuleDto.PerUnit("bare", "s2", BigDecimal.ONE),
                new RuleDto.AppearanceBonus("app", BigDecimal.ONE),
                new RuleDto.RoleConditional(
                    "cond", List.of("r"), new RuleDto.PerUnit("inner", "s1", BigDecimal.ONE)),
                new RuleDto.RoleConditional(
                    "cond-own",
                    List.of("r"),
                    new RuleDto.PerUnit("inner2", "s1", BigDecimal.ONE),
                    "Outer label")));
    var stats = List.of(new StatDto("s1", "Stat one", "integer", List.of()));
    var line = StatLines.of().stat("s1", 1).stat("s2", 1).build();

    var result =
        PointsEvaluator.evaluate(
            rules, new AppearanceRuleDto("s1", BigDecimal.ONE), stats, "r", line);

    assertThat(result.lines())
        .extracting(PointsLineDto::ruleId, PointsLineDto::label)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("own", "Own label"),
            org.assertj.core.groups.Tuple.tuple("stat", "Stat one"),
            org.assertj.core.groups.Tuple.tuple("bare", "bare"),
            org.assertj.core.groups.Tuple.tuple("app", "app"),
            org.assertj.core.groups.Tuple.tuple("cond", "Stat one"),
            org.assertj.core.groups.Tuple.tuple("cond-own", "Outer label"));
  }

  @Test
  void evaluatingTwiceGivesIdenticalOutput() {
    var line =
        StatLines.of()
            .stat("runs", 104)
            .stat("balls-faced", 55)
            .stat("strike-rate", "189.09")
            .stat("in-playing-xi", 1)
            .build();

    var first = eval("BAT", line);
    var second = eval("BAT", line);

    assertThat(second).isEqualTo(first);
    assertThat(second.lines()).isEqualTo(first.lines());
  }

  // ---- threshold bonus (AC-3) ----

  @Test
  void aMilestoneNeedsItsThresholdAndEveryTierReachedAdds() {
    var at49 = eval("BAT", StatLines.of().stat("runs", 49).build());
    var at50 = eval("BAT", StatLines.of().stat("runs", 50).build());
    var at100 = eval("BAT", StatLines.of().stat("runs", 100).build());

    assertRule(at49, "fifty-bonus", null);
    assertRule(at49, "century-bonus", null);
    assertRule(at50, "fifty-bonus", "10");
    assertRule(at50, "century-bonus", null);
    assertRule(at100, "fifty-bonus", "10");
    assertRule(at100, "century-bonus", "20");
    assertThat(at49.total()).isEqualByComparingTo("49");
    assertThat(at50.total()).isEqualByComparingTo("60");
    assertThat(at100.total()).isEqualByComparingTo("130");
  }

  @Test
  void aSingleRuleWithSeveralTiersAddsEveryTierReachedInOneLine() {
    var rules =
        new RuleSetDto(
            List.of(
                new RuleDto.ThresholdBonus(
                    "t",
                    "s",
                    List.of(
                        new RuleDto.Tier(new BigDecimal("3"), new BigDecimal("4")),
                        new RuleDto.Tier(new BigDecimal("5"), new BigDecimal("8")),
                        new RuleDto.Tier(new BigDecimal("8"), new BigDecimal("1.5"))))));

    assertThat(eval(rules, null, "x", StatLines.of().stat("s", 2).build()).lines()).isEmpty();
    assertThat(eval(rules, null, "x", StatLines.of().stat("s", 3).build()).total())
        .isEqualByComparingTo("4");
    assertThat(eval(rules, null, "x", StatLines.of().stat("s", 5).build()).total())
        .isEqualByComparingTo("12");
    var all = eval(rules, null, "x", StatLines.of().stat("s", 9).build());
    assertThat(all.total()).isEqualByComparingTo("13.5");
    assertThat(all.lines()).hasSize(1);
  }

  // ---- banded rate (AC-4) ----

  static EvaluationDto batting(String strikeRate, long balls) {
    return eval(
        "BAT",
        StatLines.of()
            .stat("strike-rate", strikeRate)
            .stat("balls-faced", balls)
            .stat("in-playing-xi", 1)
            .build());
  }

  static EvaluationDto bowling(String economy, long balls) {
    return eval(
        "BOWL",
        StatLines.of()
            .stat("economy", economy)
            .stat("balls-bowled", balls)
            .stat("in-playing-xi", 1)
            .build());
  }

  @Test
  void strikeRateBoundariesTakeTheHigherBand() {
    assertRule(batting("170", 20), "strike-rate-bands", "6");
    assertRule(batting("169.99", 20), "strike-rate-bands", "4");
    assertRule(batting("150", 20), "strike-rate-bands", "4");
    assertRule(batting("149.99", 20), "strike-rate-bands", "2");
    assertRule(batting("130", 20), "strike-rate-bands", "2");
    assertRule(batting("129.99", 20), "strike-rate-bands", null);
    assertRule(batting("70", 20), "strike-rate-bands", null);
    assertRule(batting("69.99", 20), "strike-rate-bands", "-2");
    assertRule(batting("60", 20), "strike-rate-bands", "-2");
    assertRule(batting("59.99", 20), "strike-rate-bands", "-4");
    assertRule(batting("0.01", 20), "strike-rate-bands", "-4");
    assertRule(batting("250", 20), "strike-rate-bands", "6");
  }

  @Test
  void economyBoundariesTakeTheHigherBand() {
    assertRule(bowling("4.99", 12), "economy-bands", "6");
    assertRule(bowling("5", 12), "economy-bands", "4");
    assertRule(bowling("5.99", 12), "economy-bands", "4");
    assertRule(bowling("6", 12), "economy-bands", "2");
    assertRule(bowling("6.99", 12), "economy-bands", "2");
    assertRule(bowling("7", 12), "economy-bands", null);
    assertRule(bowling("9.99", 12), "economy-bands", null);
    assertRule(bowling("10", 12), "economy-bands", "-2");
    assertRule(bowling("11.99", 12), "economy-bands", "-2");
    assertRule(bowling("12", 12), "economy-bands", "-4");
    assertRule(bowling("36", 12), "economy-bands", "-4");
  }

  @Test
  void aBandNeedsItsMinimumVolume() {
    assertRule(batting("200", 9), "strike-rate-bands", null);
    assertRule(batting("200", 10), "strike-rate-bands", "6");
    assertRule(bowling("4", 11), "economy-bands", null);
    assertRule(bowling("4", 12), "economy-bands", "6");
  }

  @Test
  void aMissingRateOrDenominatorGivesNoBand() {
    var noRate =
        eval("BAT", StatLines.of().stat("balls-faced", 20).stat("in-playing-xi", 1).build());
    var notInXi =
        eval("BAT", StatLines.of().stat("strike-rate", 200).stat("balls-faced", 20).build());

    assertRule(noRate, "strike-rate-bands", null);
    assertRule(notInXi, "strike-rate-bands", null);
  }

  @Test
  void aBandedRuleWithoutAMinimumVolumeAlwaysApplies() {
    var rules =
        new RuleSetDto(
            List.of(
                new RuleDto.BandedRate(
                    "b",
                    "n",
                    "d",
                    new BigDecimal("100"),
                    null,
                    List.of(
                        new RuleDto.Band(
                            BigDecimal.ZERO, new BigDecimal("50"), new BigDecimal("-1")),
                        new RuleDto.Band(
                            new BigDecimal("50"), new BigDecimal("101"), new BigDecimal("2"))))));

    assertThat(eval(rules, null, "x", StatLines.of().stat("n", 1).stat("d", 4).build()).total())
        .isEqualByComparingTo("-1");
    assertThat(eval(rules, null, "x", StatLines.of().stat("n", 4).stat("d", 4).build()).total())
        .isEqualByComparingTo("2");
  }

  // ---- role-conditional and event-type-bonus (AC-5, AC-6) ----

  @Test
  void aDuckIsMinusFiveForBatWkAndArAndNothingForBowl() {
    var duck = StatLines.of().stat("duck", 1).build();

    for (String role : List.of("BAT", "WK", "AR")) {
      var result = eval(role, duck);
      assertThat(result.total()).as(role).isEqualByComparingTo("-5");
      assertThat(result.lines())
          .extracting(PointsLineDto::ruleId, PointsLineDto::label)
          .containsExactly(org.assertj.core.groups.Tuple.tuple("duck-penalty", "Duck"));
    }
    assertThat(eval("BOWL", duck).lines()).isEmpty();
    assertThat(eval("BOWL", duck).total()).isEqualByComparingTo("0");
  }

  @Test
  void aNonDismissedZeroScoresNoDuck() {
    var notOut = eval("BAT", StatLines.of().stat("runs", 0).stat("duck", 0).build());

    assertRule(notOut, "duck-penalty", null);
    assertThat(notOut.total()).isEqualByComparingTo("0");
  }

  @Test
  void threeWicketsWithTwoLbwOrBowledAndOneCaughtIsOneHundredAndOne() {
    var line =
        StatLines.of()
            .stat("wickets", 3)
            .event("wickets", "lbw", 1)
            .event("wickets", "bowled", 1)
            .event("wickets", "caught", 1)
            .build();

    var result = eval("BOWL", line);

    assertRule(result, "wicket-points", "75");
    assertRule(result, "lbw-bonus", "8");
    assertRule(result, "bowled-bonus", "8");
    assertRule(result, "three-wicket-bonus", "10");
    assertRule(result, "five-wicket-bonus", null);
    assertThat(result.total()).isEqualByComparingTo("101");
  }

  @Test
  void directRunOutsGiveTwelveAndAssistedSix() {
    var direct =
        eval("BAT", StatLines.of().stat("run-outs", 1).event("run-outs", "direct", 1).build());
    var assisted =
        eval("BAT", StatLines.of().stat("run-outs", 1).event("run-outs", "assisted", 1).build());
    var both =
        eval(
            "BAT",
            StatLines.of()
                .stat("run-outs", 2)
                .event("run-outs", "direct", 1)
                .event("run-outs", "assisted", 1)
                .build());

    assertThat(direct.total()).isEqualByComparingTo("12");
    assertThat(assisted.total()).isEqualByComparingTo("6");
    assertThat(both.total()).isEqualByComparingTo("18");
    assertThat(Map.of("direct", direct, "assisted", assisted).get("direct").lines()).hasSize(1);
  }
}
