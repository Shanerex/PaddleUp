package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.rooms.RoomsTestSupport;
import com.paddleup.rooms.dto.ScoringInputsDto;
import com.paddleup.rooms.dto.Violation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AC-12: every violation of an edited set is reported and nothing is half-checked. */
class RuleSetValidatorTest {

  static final PackDto PACK = RoomsTestSupport.cricket();
  static final ScoringInputsDto INPUTS =
      new ScoringInputsDto(
          PACK.id(),
          PACK.version(),
          PACK.roles(),
          PACK.statSchema(),
          PACK.defaultRuleSet(),
          PACK.appearanceRule(),
          null,
          null);

  static List<String> fields(RuleDto... rules) {
    return RuleSetValidator.validate(new RuleSetDto(List.of(rules)), INPUTS).stream()
        .map(Violation::field)
        .toList();
  }

  static BigDecimal n(String v) {
    return new BigDecimal(v);
  }

  static RuleDto.Band band(String from, String to) {
    return new RuleDto.Band(n(from), n(to), BigDecimal.ONE);
  }

  static RuleDto.BandedRate banded(RuleDto.MinVolume min, RuleDto.Band... bands) {
    return new RuleDto.BandedRate("b", "runs", "balls-faced", n("1"), min, List.of(bands));
  }

  @Test
  void theCricketDefaultSetIsValid() {
    assertThat(RuleSetValidator.validate(PACK.defaultRuleSet(), INPUTS)).isEmpty();
  }

  @Test
  void aBandedRateScaleMustBeAboveZero() {
    for (String scale : List.of("0", "-1")) {
      var rule =
          new RuleDto.BandedRate(
              "b", "runs", "balls-faced", n(scale), null, List.of(band("0", "10")));
      assertThat(fields(rule)).containsExactly("rules[0].scale");
    }
  }

  @Test
  void anUnknownStatNamesTheField() {
    assertThat(fields(new RuleDto.PerUnit("a", "nope", BigDecimal.ONE)))
        .containsExactly("rules[0].stat");
    assertThat(
            RuleSetValidator.validate(
                new RuleSetDto(List.of(new RuleDto.PerUnit("a", "nope", BigDecimal.ONE))), INPUTS))
        .extracting(Violation::message)
        .containsExactly("'nope' is not a stat of this room");
  }

  @Test
  void anUnknownRoleInARoleConditionalIsReported() {
    var rule =
        new RuleDto.RoleConditional(
            "c", List.of("BAT", "ZZ"), new RuleDto.PerUnit("p", "runs", BigDecimal.ONE));
    assertThat(fields(rule)).containsExactly("rules[0].roles");
  }

  @Test
  void anUnknownEventTypeIsReported() {
    assertThat(fields(new RuleDto.EventTypeBonus("e", "wickets", "nope", BigDecimal.ONE)))
        .containsExactly("rules[0].eventType");
    assertThat(fields(new RuleDto.EventTypeBonus("e", "runs", "lbw", BigDecimal.ONE)))
        .containsExactly("rules[0].eventType");
  }

  @Test
  void overlappingBandsAreReportedOnTheLaterBand() {
    assertThat(fields(banded(null, band("0", "10"), band("5", "20"))))
        .containsExactly("rules[0].bands[1]");
    assertThat(fields(banded(null, band("0", "10"), band("10", "20")))).isEmpty();
  }

  @Test
  void aBandWithFromAtOrAboveToIsReported() {
    assertThat(fields(banded(null, band("10", "10")))).containsExactly("rules[0].bands[0]");
    assertThat(fields(banded(null, band("10", "5")))).containsExactly("rules[0].bands[0]");
  }

  @Test
  void aNegativeMinimumVolumeIsReported() {
    assertThat(fields(banded(new RuleDto.MinVolume("balls-faced", n("-1")), band("0", "10"))))
        .containsExactly("rules[0].minVolume.atLeast");
    assertThat(fields(banded(new RuleDto.MinVolume("balls-faced", n("0")), band("0", "10"))))
        .isEmpty();
  }

  @Test
  void aMinimumVolumeOnAnUnknownStatIsReported() {
    assertThat(fields(banded(new RuleDto.MinVolume("nope", n("1")), band("0", "10"))))
        .containsExactly("rules[0].minVolume.stat");
  }

  @Test
  void moreThanOneHundredRulesIsOneViolation() {
    var rules = new ArrayList<RuleDto>();
    for (int i = 0; i < 101; i++) {
      rules.add(new RuleDto.PerUnit("r" + i, "runs", BigDecimal.ONE));
    }

    var found = RuleSetValidator.validate(new RuleSetDto(rules), INPUTS);

    assertThat(found).extracting(Violation::field).containsExactly("rules");
    var hundred = new ArrayList<>(rules.subList(0, 100));
    assertThat(RuleSetValidator.validate(new RuleSetDto(hundred), INPUTS)).isEmpty();
  }

  @Test
  void aLabelOverSixtyCharactersOrWithMarkupIsReported() {
    assertThat(fields(new RuleDto.PerUnit("a", "runs", BigDecimal.ONE, "x".repeat(61))))
        .containsExactly("rules[0].label");
    assertThat(fields(new RuleDto.PerUnit("a", "runs", BigDecimal.ONE, "x".repeat(60)))).isEmpty();
    assertThat(fields(new RuleDto.PerUnit("a", "runs", BigDecimal.ONE, "<b>hi</b>")))
        .containsExactly("rules[0].label");
  }

  @Test
  void anIdThatIsBlankLongOrMarkupIsReported() {
    assertThat(fields(new RuleDto.PerUnit(" ", "runs", BigDecimal.ONE)))
        .containsExactly("rules[0].id");
    assertThat(fields(new RuleDto.PerUnit("r".repeat(61), "runs", BigDecimal.ONE)))
        .containsExactly("rules[0].id");
    assertThat(fields(new RuleDto.PerUnit("<script>", "runs", BigDecimal.ONE)))
        .containsExactly("rules[0].id");
  }

  @Test
  void aDuplicateIdIsReportedOnTheLaterRuleIncludingNestedOnes() {
    assertThat(
            fields(
                new RuleDto.PerUnit("a", "runs", BigDecimal.ONE),
                new RuleDto.PerUnit("a", "fours", BigDecimal.ONE)))
        .containsExactly("rules[1].id");
    assertThat(
            fields(
                new RuleDto.PerUnit("a", "runs", BigDecimal.ONE),
                new RuleDto.RoleConditional(
                    "c", List.of("BAT"), new RuleDto.PerUnit("a", "fours", BigDecimal.ONE))))
        .containsExactly("rules[1].rule.id");
  }

  @Test
  void roleConditionalsNestingMoreThanThreeDeepAreReported() {
    RuleDto rule = new RuleDto.AppearanceBonus("leaf", BigDecimal.ONE);
    for (int i = 1; i <= 4; i++) {
      rule = new RuleDto.RoleConditional("c" + i, List.of("BAT"), rule);
    }
    assertThat(fields(rule)).containsExactly("rules[0].rule.rule.rule");
    RuleDto three = new RuleDto.AppearanceBonus("leaf", BigDecimal.ONE);
    for (int i = 1; i <= 3; i++) {
      three = new RuleDto.RoleConditional("d" + i, List.of("BAT"), three);
    }
    assertThat(fields(three)).isEmpty();
  }

  @Test
  void moreThanFiftyTiersOrBandsIsReported() {
    var tiers = new ArrayList<RuleDto.Tier>();
    var bands = new ArrayList<RuleDto.Band>();
    for (int i = 0; i < 51; i++) {
      tiers.add(new RuleDto.Tier(BigDecimal.valueOf(i), BigDecimal.ONE));
      bands.add(band(String.valueOf(i), String.valueOf(i + 1)));
    }
    assertThat(fields(new RuleDto.ThresholdBonus("t", "runs", tiers)))
        .containsExactly("rules[0].tiers");
    assertThat(fields(banded(null, bands.toArray(RuleDto.Band[]::new))))
        .containsExactly("rules[0].bands");
  }

  @Test
  void numbersOutsideTheBoundsAreReported() {
    assertThat(fields(new RuleDto.PerUnit("p", "runs", n("1000001"))))
        .containsExactly("rules[0].points");
    assertThat(fields(new RuleDto.PerUnit("p", "runs", n("0.00001"))))
        .containsExactly("rules[0].points");
    assertThat(fields(new RuleDto.PerUnit("p", "runs", n("0E+2000000000"))))
        .containsExactly("rules[0].points");
    assertThat(fields(new RuleDto.PerUnit("p", "runs", n("-1000000")))).isEmpty();
    assertThat(fields(new RuleDto.PerUnit("p", "runs", n("0.0001")))).isEmpty();
  }

  @Test
  void aMissingRequiredValueIsReportedInsteadOfLeftToFail() {
    assertThat(fields(new RuleDto.PerUnit("p", "runs", null))).containsExactly("rules[0].points");
    assertThat(fields(new RuleDto.PerUnit("p", null, BigDecimal.ONE)))
        .containsExactly("rules[0].stat");
    assertThat(fields(new RuleDto.RoleConditional("c", List.of("BAT"), null)))
        .containsExactly("rules[0].rule");
  }

  @Test
  void everyViolationIsReportedTogether() {
    var found =
        fields(
            new RuleDto.PerUnit("a", "nope", BigDecimal.ONE),
            new RuleDto.PerUnit("a", "runs", n("2000000")),
            banded(new RuleDto.MinVolume("balls-faced", n("-5")), band("0", "10"), band("5", "9")),
            new RuleDto.RoleConditional(
                "c",
                List.of("ZZ"),
                new RuleDto.PerUnit("p", "runs", BigDecimal.ONE),
                "y".repeat(61)));

    assertThat(found)
        .containsExactlyInAnyOrder(
            "rules[0].stat",
            "rules[1].id",
            "rules[1].points",
            "rules[2].minVolume.atLeast",
            "rules[2].bands[1]",
            "rules[3].roles",
            "rules[3].label");
  }
}
