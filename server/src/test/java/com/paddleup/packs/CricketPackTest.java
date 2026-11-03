package com.paddleup.packs;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.config.PacksConfig;
import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.PackSummaryDto;
import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.dto.RoleDto;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.packs.dto.TagDto;
import com.paddleup.packs.service.PackService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The shipped Cricket T20 pack, read through the pack service from the default directory. */
class CricketPackTest {

  static final String ID = "cricket-t20";

  /** Starts the app context on the default pack directory and hands the pack to the check. */
  static void withPack(Consumer<PackDto> check) {
    new ApplicationContextRunner()
        .withUserConfiguration(PacksConfig.class, PackService.class)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              check.accept(ctx.getBean(PackService.class).get(ID));
            });
  }

  @Test
  void defaultDirectoryRegistersTheCricketPackAsManualEntryOnly() {
    new ApplicationContextRunner()
        .withUserConfiguration(PacksConfig.class, PackService.class)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx.getBean(PackService.class).list())
                  .contains(new PackSummaryDto(ID, "1.0.0", true));
            });
  }

  @Test
  void rolesAreExactlyTheFourEachWithALabel() {
    withPack(
        pack -> {
          assertThat(pack.roles())
              .extracting(RoleDto::id)
              .containsExactly("BAT", "BOWL", "AR", "WK");
          assertThat(pack.roles()).allSatisfy(r -> assertThat(r.label()).isNotBlank());
        });
  }

  @Test
  void tagsAreExactlyCappedUncappedOverseas() {
    withPack(
        pack -> {
          assertThat(pack.tags())
              .extracting(TagDto::id)
              .containsExactly("capped", "uncapped", "overseas");
          assertThat(pack.tags()).allSatisfy(t -> assertThat(t.label()).isNotBlank());
        });
  }

  @Test
  void onlyRestrictedCategoryIsOverseasMaxEightOnTheOverseasTag() {
    withPack(
        pack -> {
          assertThat(pack.restrictedCategories()).hasSize(1);
          var category = pack.restrictedCategories().get(0);
          assertThat(category.id()).isEqualTo("overseas-cap");
          assertThat(category.tag()).isEqualTo("overseas");
          assertThat(category.max()).isEqualTo(8);
        });
  }

  static PresetDto preset(PackDto pack, String id) {
    return pack.presets().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
  }

  /** Test-only ladder lookup: the step of the last rung whose {@code from} is at most the bid. */
  static long nextIncrement(List<PresetDto.IncrementStep> ladder, long bid) {
    return ladder.stream().filter(r -> r.from() <= bid).reduce((a, b) -> b).orElseThrow().step();
  }

  @Test
  void standardPresetHoldsMoneySquadAndMinimumsInLakh() {
    withPack(
        pack -> {
          var p = preset(pack, "standard");
          assertThat(p.purse()).isEqualTo(12_000);
          assertThat(p.currencyCode()).isEqualTo("INR");
          assertThat(p.displayUnit()).isEqualTo(new PresetDto.DisplayUnit("Crore", 100));
          assertThat(p.squadMin()).isEqualTo(18);
          assertThat(p.squadMax()).isEqualTo(25);
          assertThat(p.roleMinimums()).isEqualTo(Map.of("WK", 2, "BAT", 5, "BOWL", 5, "AR", 3));
          assertThat(p.incrementLadder())
              .containsExactly(
                  new PresetDto.IncrementStep(0, 5),
                  new PresetDto.IncrementStep(100, 10),
                  new PresetDto.IncrementStep(200, 20),
                  new PresetDto.IncrementStep(500, 25));
        });
  }

  @ParameterizedTest(name = "next increment at {0}L is {1}L")
  @CsvSource({"90, 5", "100, 10", "190, 10", "200, 20", "490, 20", "500, 25"})
  void nextIncrementFollowsTheLadderAndAThresholdBelongsToTheHigherStep(long bid, long step) {
    withPack(
        pack ->
            assertThat(nextIncrement(preset(pack, "standard").incrementLadder(), bid))
                .isEqualTo(step));
  }

  @Test
  void retentionReadsCountsCapsSlabsAndRtmRules() {
    withPack(
        pack -> {
          var r = preset(pack, "standard").retention();
          assertThat(r.maxRetentions()).isEqualTo(6);
          assertThat(r.rtmCards()).isEqualTo(6);
          assertThat(r.tagLimits()).isEqualTo(Map.of("capped", 5, "uncapped", 2));
          assertThat(r.slabs())
              .containsExactly(
                  new PresetDto.Slab("capped", List.of(1800L, 1400L, 1100L, 1800L, 1400L)),
                  new PresetDto.Slab("uncapped", List.of(400L)));
          assertThat(r.rtmFromSeason()).isEqualTo(2);
          assertThat(r.rtmCounterRaise()).isTrue();
          assertThat(preset(pack, "standard").poolSize()).isNull();
        });
  }

  @Test
  void shortPoolPresetIsTheStandardOneWithAPoolOfEighty() {
    withPack(
        pack -> {
          var standard = preset(pack, "standard");
          var shortPool = preset(pack, "short-pool");
          assertThat(shortPool.poolSize()).isEqualTo(80);
          assertThat(shortPool)
              .usingRecursiveComparison()
              .ignoringFields("id", "label", "poolSize")
              .isEqualTo(standard);
        });
  }

  /** The PRD 7.3 stat list, as stat ids (strike rate and economy are supplied, not derived). */
  static final List<String> STAT_IDS =
      List.of(
          "in-playing-xi",
          "runs",
          "balls-faced",
          "fours",
          "sixes",
          "duck",
          "strike-rate",
          "wickets",
          "balls-bowled",
          "runs-conceded",
          "maidens",
          "economy",
          "catches",
          "stumpings",
          "run-outs",
          "player-of-match");

  @Test
  void statSchemaHoldsEveryStatWithAKindAndAUnit() {
    withPack(
        pack -> {
          assertThat(pack.statSchema())
              .extracting(StatDto::id)
              .containsExactlyInAnyOrderElementsOf(STAT_IDS);
          assertThat(pack.statSchema())
              .allSatisfy(
                  st -> {
                    assertThat(st.kind()).isIn("integer", "decimal");
                    assertThat(st.unit()).isNotBlank();
                  });
        });
  }

  private static StatDto stat(PackDto pack, String id) {
    return pack.statSchema().stream().filter(st -> st.id().equals(id)).findFirst().orElseThrow();
  }

  @Test
  void ballsBowledIsAnIntegerCountOfLegalBallsAndRatesAreDecimals() {
    withPack(
        pack -> {
          assertThat(stat(pack, "balls-bowled").kind()).isEqualTo("integer");
          assertThat(stat(pack, "balls-bowled").unit()).isEqualTo("balls");
          assertThat(stat(pack, "strike-rate").kind()).isEqualTo("decimal");
          assertThat(stat(pack, "economy").kind()).isEqualTo("decimal");
          assertThat(stat(pack, "wickets").eventTypes())
              .containsExactlyInAnyOrder("bowled", "lbw", "caught", "stumped", "hit-wicket");
          assertThat(stat(pack, "run-outs").eventTypes())
              .containsExactlyInAnyOrder("direct", "assisted");
        });
  }

  @Test
  void appearanceRuleNamesAStatInTheSchemaAndCountsAnyAppearance() {
    withPack(
        pack -> {
          assertThat(pack.appearanceRule().stat()).isEqualTo("in-playing-xi");
          assertThat(pack.statSchema())
              .extracting(StatDto::id)
              .contains(pack.appearanceRule().stat());
          assertThat(pack.appearanceRule().atLeast()).isEqualByComparingTo("1");
        });
  }

  /** Every rule in file order, with the rule nested in a role-conditional following its parent. */
  static List<RuleDto> allRules(PackDto pack) {
    var all = new ArrayList<RuleDto>();
    var queue = new ArrayList<>(pack.defaultRuleSet().rules());
    for (RuleDto rule : queue) {
      all.add(rule);
      if (rule instanceof RuleDto.RoleConditional rc) {
        all.add(rc.rule());
      }
    }
    return all;
  }

  static <T extends RuleDto> T rule(PackDto pack, String id, Class<T> type) {
    var found = allRules(pack).stream().filter(r -> r.id().equals(id)).toList();
    assertThat(found).as("rule %s", id).hasSize(1);
    assertThat(found.get(0)).isInstanceOf(type);
    return type.cast(found.get(0));
  }

  static void perUnit(PackDto pack, String id, String stat, String points) {
    var r = rule(pack, id, RuleDto.PerUnit.class);
    assertThat(r.stat()).isEqualTo(stat);
    assertThat(r.points()).isEqualByComparingTo(points);
  }

  static void milestone(PackDto pack, String id, String stat, String atLeast, String points) {
    var r = rule(pack, id, RuleDto.ThresholdBonus.class);
    assertThat(r.stat()).isEqualTo(stat);
    assertThat(r.tiers()).hasSize(1);
    assertThat(r.tiers().get(0).atLeast()).isEqualByComparingTo(atLeast);
    assertThat(r.tiers().get(0).points()).isEqualByComparingTo(points);
  }

  @Test
  void battingRulesScoreRunsBoundariesAndStackedMilestones() {
    withPack(
        pack -> {
          perUnit(pack, "runs-points", "runs", "1");
          perUnit(pack, "four-bonus", "fours", "2");
          perUnit(pack, "six-bonus", "sixes", "4");
          milestone(pack, "fifty-bonus", "runs", "50", "10");
          milestone(pack, "century-bonus", "runs", "100", "20");
        });
  }

  @Test
  void duckPenaltyAppliesToBatterKeeperAndAllRounderOnly() {
    withPack(
        pack -> {
          var duck = rule(pack, "duck-penalty", RuleDto.RoleConditional.class);
          assertThat(duck.roles()).containsExactlyInAnyOrder("BAT", "WK", "AR");
          assertThat(duck.roles()).doesNotContain("BOWL");
          assertThat(duck.rule()).isInstanceOf(RuleDto.PerUnit.class);
          perUnit(pack, "duck-points", "duck", "-5");
        });
  }

  /**
   * Test-only band lookup: no points when the volume is under the minimum, else the points of the
   * band with {@code from <= rate < to}.
   */
  static Optional<BigDecimal> bandPoints(RuleDto.BandedRate rule, String rate, long volume) {
    if (BigDecimal.valueOf(volume).compareTo(rule.minVolume().atLeast()) < 0) {
      return Optional.empty();
    }
    var value = new BigDecimal(rate);
    return rule.bands().stream()
        .filter(b -> value.compareTo(b.from()) >= 0 && value.compareTo(b.to()) < 0)
        .map(RuleDto.Band::points)
        .findFirst();
  }

  static void assertBand(RuleDto.BandedRate rule, String rate, long volume, String expected) {
    var got = bandPoints(rule, rate, volume);
    if (expected == null) {
      assertThat(got).as("rate %s, volume %d", rate, volume).isEmpty();
    } else {
      assertThat(got).as("rate %s, volume %d", rate, volume).isPresent();
      assertThat(got.get()).as("rate %s", rate).isEqualByComparingTo(expected);
    }
  }

  @Test
  void strikeRateBandsAreExclusiveAndTheMostExtremeBandReplacesTheMilder() {
    withPack(
        pack -> {
          var rule = rule(pack, "strike-rate-bands", RuleDto.BandedRate.class);
          assertThat(rule.numerator()).isEqualTo("strike-rate");
          assertThat(rule.denominator()).isEqualTo("in-playing-xi");
          assertThat(rule.scale()).isEqualByComparingTo("1");
          assertThat(rule.minVolume().stat()).isEqualTo("balls-faced");
          assertThat(rule.minVolume().atLeast()).isEqualByComparingTo("10");
          assertBand(rule, "170.01", 20, "6");
          assertBand(rule, "170", 20, "4");
          assertBand(rule, "150", 20, "4");
          assertBand(rule, "149.99", 20, "2");
          assertBand(rule, "130", 20, "2");
          assertBand(rule, "129.99", 20, null);
          assertBand(rule, "70", 20, null);
          assertBand(rule, "69.99", 20, "-2");
          assertBand(rule, "60", 20, "-2");
          assertBand(rule, "59.99", 20, "-4");
          assertBand(rule, "0", 20, "-4");
          assertBand(rule, "300", 20, "6");
          assertBand(rule, "170.01", 9, null);
          assertBand(rule, "10", 10, "-4");
        });
  }

  static void eventBonus(PackDto pack, String id, String stat, String eventType, String points) {
    var r = rule(pack, id, RuleDto.EventTypeBonus.class);
    assertThat(r.stat()).isEqualTo(stat);
    assertThat(r.eventType()).isEqualTo(eventType);
    assertThat(r.points()).isEqualByComparingTo(points);
  }

  @Test
  void bowlingRulesScoreWicketsDismissalBonusesStackedMilestonesAndMaidens() {
    withPack(
        pack -> {
          perUnit(pack, "wicket-points", "wickets", "25");
          eventBonus(pack, "lbw-bonus", "wickets", "lbw", "8");
          eventBonus(pack, "bowled-bonus", "wickets", "bowled", "8");
          milestone(pack, "three-wicket-bonus", "wickets", "3", "10");
          milestone(pack, "five-wicket-bonus", "wickets", "5", "20");
          perUnit(pack, "maiden-points", "maidens", "12");
        });
  }

  @Test
  void economyBandsAreExclusiveAndTheMostExtremeBandReplacesTheMilder() {
    withPack(
        pack -> {
          var rule = rule(pack, "economy-bands", RuleDto.BandedRate.class);
          assertThat(rule.numerator()).isEqualTo("economy");
          assertThat(rule.denominator()).isEqualTo("in-playing-xi");
          assertThat(rule.scale()).isEqualByComparingTo("1");
          assertThat(rule.minVolume().stat()).isEqualTo("balls-bowled");
          assertThat(rule.minVolume().atLeast()).isEqualByComparingTo("12");
          assertBand(rule, "4.99", 12, "6");
          assertBand(rule, "0", 12, "6");
          assertBand(rule, "5", 12, "4");
          assertBand(rule, "5.99", 12, "4");
          assertBand(rule, "6", 12, "2");
          assertBand(rule, "6.99", 12, "2");
          assertBand(rule, "7", 12, null);
          assertBand(rule, "10", 12, null);
          assertBand(rule, "10.01", 12, "-2");
          assertBand(rule, "12", 12, "-2");
          assertBand(rule, "12.01", 12, "-4");
          assertBand(rule, "36", 12, "-4");
          assertBand(rule, "4.99", 11, null);
        });
  }

  @Test
  void fieldingAndOtherRulesScoreCatchesStumpingsRunOutsPlayerOfTheMatchAndAppearance() {
    withPack(
        pack -> {
          perUnit(pack, "catch-points", "catches", "8");
          milestone(pack, "catch-tier-bonus", "catches", "3", "4");
          perUnit(pack, "stumping-points", "stumpings", "12");
          eventBonus(pack, "direct-run-out-bonus", "run-outs", "direct", "12");
          eventBonus(pack, "assisted-run-out-bonus", "run-outs", "assisted", "6");
          perUnit(pack, "player-of-match-points", "player-of-match", "25");
          var appearance = rule(pack, "appearance-bonus", RuleDto.AppearanceBonus.class);
          assertThat(appearance.points()).isEqualByComparingTo("4");
        });
  }

  @Test
  void everyRuleIsOneOfTheSixPrimitivesAndHasAUniqueId() {
    withPack(
        pack -> {
          var rules = allRules(pack);
          assertThat(rules).extracting(RuleDto::id).doesNotHaveDuplicates();
          assertThat(rules)
              .allSatisfy(
                  r ->
                      assertThat(r)
                          .isInstanceOfAny(
                              RuleDto.PerUnit.class,
                              RuleDto.ThresholdBonus.class,
                              RuleDto.BandedRate.class,
                              RuleDto.RoleConditional.class,
                              RuleDto.EventTypeBonus.class,
                              RuleDto.AppearanceBonus.class));
          assertThat(rules.stream().map(r -> r.getClass().getSimpleName()).distinct())
              .containsExactlyInAnyOrder(
                  "PerUnit",
                  "ThresholdBonus",
                  "BandedRate",
                  "RoleConditional",
                  "EventTypeBonus",
                  "AppearanceBonus");
          assertThat(rules).hasSize(22);
        });
  }

  /** Twenty well-known players; the AI context states concepts only (FR-123). */
  static final List<String> PLAYER_NAMES =
      List.of(
          "Virat Kohli",
          "Rohit Sharma",
          "MS Dhoni",
          "Jasprit Bumrah",
          "Hardik Pandya",
          "Rishabh Pant",
          "KL Rahul",
          "Ravindra Jadeja",
          "Suryakumar Yadav",
          "Shubman Gill",
          "Jos Buttler",
          "Rashid Khan",
          "Andre Russell",
          "Glenn Maxwell",
          "Pat Cummins",
          "David Warner",
          "Sunil Narine",
          "Mitchell Starc",
          "Ben Stokes",
          "Yuzvendra Chahal");

  private static boolean mentions(String text, String word) {
    return Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE)
        .matcher(text)
        .find();
  }

  @Test
  void terminologyNamesTheLineupMatchAndCompetition() {
    withPack(
        pack ->
            assertThat(pack.terminology())
                .isEqualTo(
                    Map.of("lineup", "Playing XI", "match", "Match", "competition", "League")));
  }

  @Test
  void aiContextHasThreePartsAGlossaryLinePerStatAndNoPlayerNames() {
    withPack(
        pack -> {
          var text = pack.aiDomainContext();
          var parts = text.split("(?m)^## ");
          // parts[0] is whatever precedes the first heading
          assertThat(parts[0]).isBlank();
          assertThat(parts).hasSize(4);
          assertThat(parts[1]).startsWith("Head Coach knowledge");
          assertThat(parts[2]).startsWith("Stat glossary");
          assertThat(parts[3]).startsWith("Fit heuristics");
          for (int i = 1; i < 4; i++) {
            var body = parts[i].substring(parts[i].indexOf('\n') + 1);
            assertThat(body).as("part %d", i).isNotBlank();
          }
          for (var st : pack.statSchema()) {
            assertThat(parts[2])
                .as("glossary line for %s", st.id())
                .contains("\n- " + st.id() + ": ");
          }
          assertThat(parts[2]).contains("2 dp").contains("impact");
          for (var name : PLAYER_NAMES) {
            assertThat(mentions(text, name)).as(name).isFalse();
            for (var word : name.split(" ")) {
              if (word.length() > 3) {
                assertThat(mentions(text, word)).as(word).isFalse();
              }
            }
          }
        });
  }

  private static final Path SHIPPED = Path.of("../packs/cricket-t20.json").toAbsolutePath();

  @Test
  void rolesMinimumsAboveSquadMinFailStartupNamingFilePackAndBothNumbers(@TempDir Path dir)
      throws IOException {
    var edited = Files.readString(SHIPPED).replace("\"WK\": 2", "\"WK\": 6");
    assertThat(edited).isNotEqualTo(Files.readString(SHIPPED));
    Files.writeString(dir.resolve("cricket-t20.json"), edited);

    var failure = PackTestSupport.loadFailure(dir);

    assertThat(failure.violations()).isNotEmpty();
    assertThat(failure.violations())
        .allSatisfy(
            v -> {
              assertThat(v.file()).isEqualTo("cricket-t20.json");
              assertThat(v.packId()).isEqualTo("cricket-t20");
              assertThat(v.check()).isEqualTo("role-minimums-exceed-squad-min");
              assertThat(v.message()).contains("19").contains("18");
            });
    assertThat(failure.getMessage()).contains("cricket-t20.json", "cricket-t20", "19", "18");
  }

  @Test
  void cricketAndTheStubFootballPackRegisterTogetherUnderDistinctIds(@TempDir Path dir)
      throws IOException {
    Files.copy(SHIPPED, dir.resolve("cricket-t20.json"));
    Files.copy(
        PackTestSupport.STUB_DIR.resolve("stub-football.json"), dir.resolve("stub-football.json"));

    PackTestSupport.runner(dir)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx.getBean(PackService.class).list())
                  .extracting(PackSummaryDto::id)
                  .containsExactly("cricket-t20", "stub-football");
            });
  }
}
