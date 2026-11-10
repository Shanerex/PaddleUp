package com.paddleup.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.rooms.ConfigEdit;
import com.paddleup.rooms.RoomsTestSupport;
import com.paddleup.rooms.dto.AiSettingsDto;
import com.paddleup.rooms.dto.RoomConfigDto;
import com.paddleup.rooms.dto.Violation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class RoomConfigValidatorTest {

  static final PackDto PACK = RoomsTestSupport.cricket();
  final RoomConfigFactory factory = new RoomConfigFactory(RoomsTestSupport.defaults());
  final RoomConfigValidator validator = new RoomConfigValidator();
  final RoomConfigDto valid =
      factory.create(PACK, RoomsTestSupport.preset(PACK, "standard"), "Friends", "Season 1");

  private List<Violation> check(Consumer<ConfigEdit> edit, int franchises) {
    var e = ConfigEdit.of(valid);
    edit.accept(e);
    return validator.validate(e.build(), factory.snapshot(PACK), franchises);
  }

  private List<String> fields(Consumer<ConfigEdit> edit) {
    return check(edit, 0).stream().map(Violation::field).toList();
  }

  @Test
  void aPresetConfigIsValid() {
    assertThat(validator.validate(valid, factory.snapshot(PACK), 0)).isEmpty();
    var short_ = factory.create(PACK, RoomsTestSupport.preset(PACK, "short-pool"), "Friends", "");
    assertThat(validator.validate(short_, factory.snapshot(PACK), 3)).isEmpty();
  }

  @Test
  void timersOutsideTheirRangesNameTheField() {
    assertThat(fields(e -> e.openSeconds = 4)).containsExactly("openSeconds");
    assertThat(fields(e -> e.openSeconds = 121)).containsExactly("openSeconds");
    assertThat(fields(e -> e.bidSeconds = 2)).containsExactly("bidSeconds");
    assertThat(fields(e -> e.bidSeconds = 61)).containsExactly("bidSeconds");
    assertThat(fields(e -> e.gapSeconds = -1)).containsExactly("gapSeconds");
    assertThat(fields(e -> e.gapSeconds = 31)).containsExactly("gapSeconds");
  }

  @Test
  void timersAtTheEdgesOfTheirRangesAndAbsentTimersAreValid() {
    assertThat(
            fields(
                e -> {
                  e.openSeconds = 5;
                  e.bidSeconds = 3;
                  e.gapSeconds = 0;
                }))
        .isEmpty();
    assertThat(
            fields(
                e -> {
                  e.openSeconds = 120;
                  e.bidSeconds = 60;
                  e.gapSeconds = 30;
                }))
        .isEmpty();
    assertThat(
            fields(
                e -> {
                  e.openSeconds = null;
                  e.bidSeconds = null;
                  e.gapSeconds = null;
                }))
        .isEmpty();
  }

  @Test
  void roleMinimumsAboveSquadMinNamesRoleMinimums() {
    assertThat(fields(e -> e.squadMin = 10)).contains("roleMinimums");
  }

  @Test
  void squadMinAboveSquadMaxNamesSquadMin() {
    assertThat(fields(e -> e.squadMax = 17)).contains("squadMin");
  }

  @Test
  void maxManagersBelowMinNamesMaxManagers() {
    assertThat(fields(e -> e.maxManagers = 1)).contains("maxManagers");
  }

  @Test
  void minManagersBelowTwoNamesMinManagers() {
    assertThat(fields(e -> e.minManagers = 1)).contains("minManagers");
  }

  @Test
  void maxManagersBelowTheCurrentFranchiseCountNamesMaxManagers() {
    var violations = check(e -> e.maxManagers = 3, 4);
    assertThat(violations).extracting(Violation::field).contains("maxManagers");
    assertThat(check(e -> e.maxManagers = 4, 4)).isEmpty();
  }

  @Test
  void maxManagersAboveTheTwelveColoursNamesMaxManagers() {
    assertThat(fields(e -> e.maxManagers = 13)).contains("maxManagers");
  }

  @Test
  void zeroPurseNamesPurse() {
    assertThat(fields(e -> e.purse = 0)).contains("purse");
    assertThat(fields(e -> e.purse = -5)).contains("purse");
  }

  @Test
  void emptyLadderNamesIncrementLadder() {
    assertThat(fields(e -> e.incrementLadder = List.of())).containsExactly("incrementLadder");
  }

  @Test
  void aNonPositiveStepNamesThatStep() {
    assertThat(
            fields(
                e ->
                    e.incrementLadder =
                        List.of(
                            new PresetDto.IncrementStep(0, 5),
                            new PresetDto.IncrementStep(100, 0))))
        .containsExactly("incrementLadder[1].step");
  }

  @Test
  void aLadderThatDoesNotAscendNamesTheOutOfOrderStep() {
    assertThat(
            fields(
                e ->
                    e.incrementLadder =
                        List.of(
                            new PresetDto.IncrementStep(0, 5),
                            new PresetDto.IncrementStep(100, 5),
                            new PresetDto.IncrementStep(50, 5))))
        .containsExactly("incrementLadder[2].from");
  }

  @Test
  void aLadderWhoseFirstBandDoesNotStartAtZeroNamesThatBand() {
    assertThat(fields(e -> e.incrementLadder = List.of(new PresetDto.IncrementStep(1000, 100))))
        .containsExactly("incrementLadder[0].from");
  }

  @Test
  void negativeQuotaNamesThatCategory() {
    assertThat(fields(e -> e.categoryQuotas = Map.of("overseas-cap", -1)))
        .containsExactly("categoryQuotas.overseas-cap");
  }

  @Test
  void unknownCategoryNamesThatCategory() {
    assertThat(fields(e -> e.categoryQuotas = Map.of("nope", 1)))
        .containsExactly("categoryQuotas.nope");
  }

  @Test
  void unknownRoleIdNamesThatRole() {
    var minimums = new LinkedHashMap<>(valid.roleMinimums());
    minimums.put("ZZ", 1);
    assertThat(fields(e -> e.roleMinimums = minimums)).contains("roleMinimums.ZZ");
  }

  @Test
  void negativeRoleMinimumNamesThatRole() {
    assertThat(fields(e -> e.roleMinimums = Map.of("BAT", -2))).contains("roleMinimums.BAT");
  }

  @Test
  void aRetentionTagThatIsNotInTheSnapshotIsNamed() {
    assertThat(
            fields(
                e ->
                    e.retention =
                        new PresetDto.Retention(6, 6, Map.of("ghost", 1), List.of(), null, null)))
        .containsExactly("retention.tagLimits.ghost");
  }

  @Test
  void negativeLifelineCountNamesThatType() {
    assertThat(fields(e -> e.ai = new AiSettingsDto(true, Map.of("fit-check", -1), "room")))
        .containsExactly("ai.lifelines.fit-check");
  }

  @Test
  void unknownLifelineTypeAndReportVisibilityAreNamed() {
    assertThat(fields(e -> e.ai = new AiSettingsDto(true, Map.of("bogus", 1), "everyone")))
        .containsExactlyInAnyOrder("ai.lifelines.bogus", "ai.reportCardVisibility");
  }

  @Test
  void missingReportVisibilityIsAViolationNotACrash() {
    assertThat(fields(e -> e.ai = new AiSettingsDto(true, Map.of(), null)))
        .containsExactly("ai.reportCardVisibility");
  }

  @Test
  void aScoringRuleOnAStatOutsideTheSnapshotNamesTheRule() {
    var rules = new RuleSetDto(List.of(new RuleDto.PerUnit("x", "not-a-stat", BigDecimal.ONE)));
    assertThat(fields(e -> e.scoringRules = rules)).containsExactly("scoringRules.rules[0].stat");
  }

  @Test
  void aNestedRuleAndItsRolesAreChecked() {
    var nested =
        new RuleDto.RoleConditional(
            "c", List.of("BAT", "ZZ"), new RuleDto.PerUnit("p", "also-missing", BigDecimal.ONE));
    assertThat(fields(e -> e.scoringRules = new RuleSetDto(List.of(nested))))
        .containsExactlyInAnyOrder(
            "scoringRules.rules[0].roles", "scoringRules.rules[0].rule.stat");
  }

  @Test
  void bandedRateAndEventTypeStatsAreChecked() {
    var banded =
        new RuleDto.BandedRate(
            "b",
            "missing-a",
            "missing-b",
            BigDecimal.ONE,
            new RuleDto.MinVolume("missing-c", BigDecimal.TEN),
            List.of());
    assertThat(fields(e -> e.scoringRules = new RuleSetDto(List.of(banded))))
        .containsExactlyInAnyOrder(
            "scoringRules.rules[0].numerator",
            "scoringRules.rules[0].denominator",
            "scoringRules.rules[0].minVolume.stat");
    var badEvent = new RuleDto.EventTypeBonus("e", "wickets", "not-a-type", BigDecimal.ONE);
    assertThat(fields(e -> e.scoringRules = new RuleSetDto(List.of(badEvent))))
        .containsExactly("scoringRules.rules[0].eventType");
  }

  @Test
  void aBadNameIsNamedAndEveryViolationIsReportedTogether() {
    var violations =
        check(
            e -> {
              e.name = "x";
              e.purse = 0;
              e.incrementLadder = List.of();
            },
            0);
    assertThat(violations)
        .extracting(Violation::field)
        .containsExactlyInAnyOrder("name", "purse", "incrementLadder");
  }

  @Test
  void nullsInTheConfigAreViolationsNotCrashes() {
    var violations =
        check(
            e -> {
              e.ai = null;
              e.displayUnit = null;
              e.currencyCode = null;
              e.name = null;
            },
            0);
    assertThat(violations)
        .extracting(Violation::field)
        .containsExactlyInAnyOrder("ai", "displayUnit", "currencyCode", "name");
  }

  // Security review S-1: free-text fields are cleaned and capped like name and competition.

  @Test
  void currencyCodeWithMarkupOrTooLongNamesCurrencyCode() {
    assertThat(fields(e -> e.currencyCode = "<b>X</b>")).containsExactly("currencyCode");
    assertThat(fields(e -> e.currencyCode = "A".repeat(11))).containsExactly("currencyCode");
    assertThat(fields(e -> e.currencyCode = "A\u0000B")).containsExactly("currencyCode");
  }

  @Test
  void displayUnitLabelWithMarkupOrTooLongNamesDisplayUnit() {
    assertThat(fields(e -> e.displayUnit = new PresetDto.DisplayUnit("<i>Cr</i>", 100)))
        .containsExactly("displayUnit");
    assertThat(fields(e -> e.displayUnit = new PresetDto.DisplayUnit("x".repeat(21), 100)))
        .containsExactly("displayUnit");
  }

  @Test
  void displayUnitShortAndMinorAreCheckedLikeThePackChecksThem() {
    var minor = new PresetDto.DisplayUnit.Minor("Lakh", "L", 1);
    assertThat(fields(e -> e.displayUnit = new PresetDto.DisplayUnit("Crore", 100, "Cr", minor)))
        .isEmpty();
    assertThat(fields(e -> e.displayUnit = new PresetDto.DisplayUnit("Crore", 100, "<b>", null)))
        .containsExactly("displayUnit");
    assertThat(
            fields(e -> e.displayUnit = new PresetDto.DisplayUnit("Crore", 100, "TOOLONG7", null)))
        .containsExactly("displayUnit");
    var bad = new String[] {"<b>", "x".repeat(21)};
    for (String label : bad) {
      var m = new PresetDto.DisplayUnit.Minor(label, "L", 1);
      assertThat(fields(e -> e.displayUnit = new PresetDto.DisplayUnit("Crore", 100, "Cr", m)))
          .containsExactly("displayUnit");
    }
    for (long divisor : new long[] {0, -1, 100, 101}) {
      var m = new PresetDto.DisplayUnit.Minor("Lakh", "L", divisor);
      assertThat(fields(e -> e.displayUnit = new PresetDto.DisplayUnit("Crore", 100, "Cr", m)))
          .containsExactly("displayUnit");
    }
  }

  @Test
  void aScoringRuleIdWithMarkupOrTooLongIsNamed() {
    var markup = new RuleSetDto(List.of(new RuleDto.PerUnit("<script>", "runs", BigDecimal.ONE)));
    var tooLong =
        new RuleSetDto(List.of(new RuleDto.PerUnit("r".repeat(61), "runs", BigDecimal.ONE)));
    var blank = new RuleSetDto(List.of(new RuleDto.PerUnit(" ", "runs", BigDecimal.ONE)));
    assertThat(fields(e -> e.scoringRules = markup)).containsExactly("scoringRules.rules[0].id");
    assertThat(fields(e -> e.scoringRules = tooLong)).containsExactly("scoringRules.rules[0].id");
    assertThat(fields(e -> e.scoringRules = blank)).containsExactly("scoringRules.rules[0].id");
  }

  private String tag() {
    return factory.snapshot(PACK).tags().get(0).id();
  }

  // Security review S-2: sizes are bounded and unknown keys are not echoed unbounded.

  @Test
  void anOversizedLadderIsRefusedWithOneViolation() {
    var steps = new ArrayList<PresetDto.IncrementStep>();
    for (int i = 0; i < 51; i++) steps.add(new PresetDto.IncrementStep(i, 1));
    assertThat(fields(e -> e.incrementLadder = steps)).containsExactly("incrementLadder");
  }

  @Test
  void tooManyScoringRulesIsRefusedWithOneViolation() {
    var rules = new ArrayList<RuleDto>();
    for (int i = 0; i < 101; i++) rules.add(new RuleDto.PerUnit("r" + i, "runs", BigDecimal.ONE));
    assertThat(fields(e -> e.scoringRules = new RuleSetDto(rules)))
        .containsExactly("scoringRules.rules");
  }

  @Test
  void tooManyRetentionSlabsOrPricesIsRefused() {
    var slabs = new ArrayList<PresetDto.Slab>();
    for (int i = 0; i < 51; i++) slabs.add(new PresetDto.Slab(tag(), List.of(1L)));
    assertThat(
            fields(e -> e.retention = new PresetDto.Retention(6, 6, Map.of(), slabs, null, null)))
        .containsExactly("retention.slabs");
    var prices = new ArrayList<Long>();
    for (int i = 0; i < 51; i++) prices.add(1L);
    assertThat(
            fields(
                e ->
                    e.retention =
                        new PresetDto.Retention(
                            6,
                            6,
                            Map.of(),
                            List.of(new PresetDto.Slab(tag(), prices)),
                            null,
                            null)))
        .containsExactly("retention.slabs[0].prices");
  }

  // Security review S-6: nested rule content is bounded too.

  @Test
  void oversizedNestedRuleContentIsRefusedWithOneViolationEach() {
    var roles = new ArrayList<String>();
    for (int i = 0; i < 51; i++) roles.add("x".repeat(500) + i);
    var cond =
        new RuleDto.RoleConditional("c", roles, new RuleDto.AppearanceBonus("a", BigDecimal.ONE));
    var violations = check(e -> e.scoringRules = new RuleSetDto(List.of(cond)), 0);
    assertThat(violations)
        .extracting(Violation::field)
        .containsExactly("scoringRules.rules[0].roles");
    assertThat(violations.get(0).message()).doesNotContain("xxxx");

    var tiers = new ArrayList<RuleDto.Tier>();
    for (int i = 0; i < 51; i++) tiers.add(new RuleDto.Tier(BigDecimal.valueOf(i), BigDecimal.ONE));
    var threshold = new RuleDto.ThresholdBonus("t", "runs", tiers);
    assertThat(fields(e -> e.scoringRules = new RuleSetDto(List.of(threshold))))
        .containsExactly("scoringRules.rules[0].tiers");

    var bands = new ArrayList<RuleDto.Band>();
    for (int i = 0; i < 51; i++)
      bands.add(new RuleDto.Band(BigDecimal.valueOf(i), null, BigDecimal.ONE));
    var banded = new RuleDto.BandedRate("b", "runs", "runs", BigDecimal.TEN, null, bands);
    assertThat(fields(e -> e.scoringRules = new RuleSetDto(List.of(banded))))
        .containsExactly("scoringRules.rules[0].bands");
  }

  // Security review S-8: user-supplied decimals are bounded in size and precision.

  private static final BigDecimal HUGE = new BigDecimal("1e999999999");
  private static final BigDecimal TOO_PRECISE = new BigDecimal("0.00001");

  private List<String> ruleFields(RuleDto rule) {
    return fields(e -> e.scoringRules = new RuleSetDto(List.of(rule)));
  }

  @Test
  void anAbsurdlyLargeOrPreciseDecimalIsOneViolationOnItsField() {
    assertThat(ruleFields(new RuleDto.PerUnit("p", "runs", HUGE)))
        .containsExactly("scoringRules.rules[0].points");
    assertThat(ruleFields(new RuleDto.PerUnit("p", "runs", HUGE.negate())))
        .containsExactly("scoringRules.rules[0].points");
    assertThat(ruleFields(new RuleDto.PerUnit("p", "runs", TOO_PRECISE)))
        .containsExactly("scoringRules.rules[0].points");
    assertThat(ruleFields(new RuleDto.PerUnit("p", "runs", new BigDecimal("1000001"))))
        .containsExactly("scoringRules.rules[0].points");
    assertThat(ruleFields(new RuleDto.EventTypeBonus("e", "wickets", "bowled", HUGE)))
        .containsExactly("scoringRules.rules[0].points");
    assertThat(ruleFields(new RuleDto.AppearanceBonus("a", HUGE)))
        .containsExactly("scoringRules.rules[0].points");
  }

  @Test
  void decimalsAtTheBoundsAreAccepted() {
    assertThat(ruleFields(new RuleDto.PerUnit("p", "runs", new BigDecimal("1000000")))).isEmpty();
    assertThat(ruleFields(new RuleDto.PerUnit("p", "runs", new BigDecimal("-0.0001")))).isEmpty();
    assertThat(ruleFields(new RuleDto.PerUnit("p", "runs", new BigDecimal("1E+3")))).isEmpty();
  }

  // Security review S-10: the lower end of scale is bounded too, so Postgres never sees it.
  @Test
  void aHugeNegativeScaleIsRefusedEvenForZero() {
    assertThat(ruleFields(new RuleDto.PerUnit("p", "runs", new BigDecimal("0E+2000000000"))))
        .containsExactly("scoringRules.rules[0].points");
    assertThat(ruleFields(new RuleDto.PerUnit("p", "runs", new BigDecimal("0E-2000000000"))))
        .containsExactly("scoringRules.rules[0].points");
    assertThat(ruleFields(new RuleDto.PerUnit("p", "runs", new BigDecimal("0E+7"))))
        .containsExactly("scoringRules.rules[0].points");
    assertThat(ruleFields(new RuleDto.PerUnit("p", "runs", new BigDecimal("0E+6")))).isEmpty();
    assertThat(ruleFields(new RuleDto.PerUnit("p", "runs", new BigDecimal("-1E+6")))).isEmpty();
  }

  @Test
  void tierBandAndScaleDecimalsAreBoundedOneViolationEach() {
    var tiers = List.of(new RuleDto.Tier(HUGE, HUGE), new RuleDto.Tier(BigDecimal.ONE, HUGE));
    assertThat(ruleFields(new RuleDto.ThresholdBonus("t", "runs", tiers)))
        .containsExactly(
            "scoringRules.rules[0].tiers[0].atLeast",
            "scoringRules.rules[0].tiers[0].points",
            "scoringRules.rules[0].tiers[1].points");

    var bands = List.of(new RuleDto.Band(HUGE, HUGE.negate(), HUGE));
    var banded =
        new RuleDto.BandedRate(
            "b", "runs", "runs", HUGE, new RuleDto.MinVolume("runs", HUGE), bands);
    assertThat(ruleFields(banded))
        .containsExactly(
            "scoringRules.rules[0].scale",
            "scoringRules.rules[0].minVolume.atLeast",
            "scoringRules.rules[0].bands[0].from",
            "scoringRules.rules[0].bands[0].to",
            "scoringRules.rules[0].bands[0].points");
  }

  @Test
  void aDecimalViolationDoesNotEchoTheValue() {
    var violations =
        check(
            e -> e.scoringRules = new RuleSetDto(List.of(new RuleDto.PerUnit("p", "runs", HUGE))),
            0);
    assertThat(violations).hasSize(1);
    assertThat(violations.get(0).message()).doesNotContain("999999999");
  }

  private static RuleDto nested(int depth) {
    RuleDto rule = new RuleDto.AppearanceBonus("leaf", BigDecimal.ONE);
    for (int i = 1; i < depth; i++) rule = new RuleDto.RoleConditional("c" + i, List.of(), rule);
    return rule;
  }

  @Test
  void roleConditionalNestingIsCappedAtThreeLevels() {
    assertThat(fields(e -> e.scoringRules = new RuleSetDto(List.of(nested(4))))).isEmpty();
    assertThat(fields(e -> e.scoringRules = new RuleSetDto(List.of(nested(5)))))
        .containsExactly("scoringRules.rules[0].rule.rule.rule");
    // A very deep chain stops at the cap instead of recursing to the bottom.
    assertThat(fields(e -> e.scoringRules = new RuleSetDto(List.of(nested(2000)))))
        .containsExactly("scoringRules.rules[0].rule.rule.rule");
  }

  @Test
  void anOversizedMapIsRefusedWithoutEchoingItsKeys() {
    var big = new LinkedHashMap<String, Integer>();
    for (int i = 0; i < 51; i++) big.put("k" + i, 1);
    assertThat(fields(e -> e.roleMinimums = big)).containsExactly("roleMinimums");
    assertThat(fields(e -> e.categoryQuotas = big)).containsExactly("categoryQuotas");
    assertThat(fields(e -> e.ai = new AiSettingsDto(true, big, "room")))
        .containsExactly("ai.lifelines");
  }

  @Test
  void aLongUnknownKeyIsClippedInTheViolation() {
    String key = "k".repeat(5000);
    var violations = check(e -> e.categoryQuotas = Map.of(key, 1), 0);
    assertThat(violations).hasSize(1);
    assertThat(violations.get(0).field().length()).isLessThan(100);
    assertThat(violations.get(0).message().length()).isLessThan(200);
  }
}
