package com.paddleup.packs.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.PackTestSupport;
import com.paddleup.packs.dto.FieldError;
import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.PlayerInput;
import com.paddleup.packs.dto.PresetDto;
import com.paddleup.rooms.RoomsTestSupport;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** AC-3, AC-8: one row validator for imports and room edits, on any pack. */
class PlayerRowRulesTest {

  /** A pack, a role and tag it declares, and a price it accepts. */
  record Case(String label, PackDto pack, String role, String tagA, String tagB, String price) {
    @Override
    public String toString() {
      return label;
    }
  }

  static Stream<Arguments> packs() {
    return Stream.of(
        Arguments.of(
            new Case("cricket", RoomsTestSupport.cricket(), "BAT", "capped", "overseas", "20")),
        Arguments.of(
            new Case(
                "stub",
                RoomsTestSupport.load(PackTestSupport.STUB_DIR, "stub-football"),
                "gk",
                "local",
                "foreign",
                "500000")));
  }

  private static PlayerRowRules rules(Case c) {
    return PlayerRowRules.forPack(c.pack());
  }

  private static PlayerInput row(Case c) {
    return new PlayerInput(
        "Ann Lee", "Chile", c.role(), List.of(c.tagA()), c.price(), "Marquee", null);
  }

  private static List<String> fields(PlayerRowRules.Result r) {
    return r.errors().stream().map(FieldError::field).toList();
  }

  @ParameterizedTest
  @MethodSource("packs")
  void aValidRowIsAcceptedAndCleaned(Case c) {
    var in =
        new PlayerInput(
            " <b>Ann</b>   Lee ",
            "Chile",
            c.role(),
            List.of(c.tagA(), c.tagB()),
            c.price(),
            "Marquee",
            " p-1 ");

    var result = rules(c).check(in);

    assertThat(result.errors()).isEmpty();
    assertThat(result.player().name()).isEqualTo("Ann Lee");
    assertThat(result.player().tags()).containsExactly(c.tagA(), c.tagB());
    assertThat(result.player().providerId()).isEqualTo("p-1");
  }

  @ParameterizedTest
  @MethodSource("packs")
  void anUnknownRoleIsRejectedWithTheRoleField(Case c) {
    var in = new PlayerInput("Ann", "Chile", "NOPE", List.of(), c.price(), "Marquee", null);

    var result = rules(c).check(in);

    assertThat(fields(result)).containsExactly("role");
    assertThat(result.errors().get(0).value()).isEqualTo("NOPE");
  }

  @ParameterizedTest
  @MethodSource("packs")
  void anUnknownOrDuplicateTagIsRejected(Case c) {
    var unknown = new PlayerInput("Ann", "Chile", c.role(), List.of("nope"), c.price(), "S", null);
    var twice =
        new PlayerInput(
            "Ann", "Chile", c.role(), List.of(c.tagA(), c.tagA()), c.price(), "S", null);

    assertThat(fields(rules(c).check(unknown))).containsExactly("tags");
    assertThat(fields(rules(c).check(twice))).containsExactly("tags");
    assertThat(rules(c).check(twice).errors().get(0).message()).contains("twice");
  }

  @ParameterizedTest
  @MethodSource("packs")
  void aPriceOfZeroOrLessIsRejected(Case c) {
    for (String bad : List.of("0", "-20")) {
      var in = new PlayerInput("Ann", "Chile", c.role(), List.of(), bad, "S", null);
      var result = rules(c).check(in);
      assertThat(fields(result)).containsExactly("base_price");
      assertThat(result.errors().get(0).message()).isEqualTo("Base price must be above zero.");
    }
  }

  @ParameterizedTest
  @MethodSource("packs")
  void aPriceThatIsNotAWholeNumberOrMissingIsRejected(Case c) {
    for (String bad : List.of("12.5", "abc", "", "1e3")) {
      var in = new PlayerInput("Ann", "Chile", c.role(), List.of(), bad, "S", null);
      assertThat(fields(rules(c).check(in))).containsExactly("base_price");
    }
  }

  @ParameterizedTest
  @MethodSource("packs")
  void aPriceOverThePurseOrOffTheLadderGetsTheGenericMessage(Case c) {
    PresetDto preset = c.pack().presets().get(0);
    String overPurse = Long.toString(preset.purse() + preset.incrementLadder().get(0).step());
    String offLadder = Long.toString(Long.parseLong(c.price()) + 1);
    for (String bad : List.of(overPurse, offLadder)) {
      var in = new PlayerInput("Ann", "Chile", c.role(), List.of(), bad, "S", null);
      var result = rules(c).check(in);
      assertThat(fields(result)).containsExactly("base_price");
      assertThat(result.errors().get(0).message())
          .startsWith("That base price isn't allowed in ")
          .contains(c.pack().display().name());
    }
  }

  @Test
  void aRoomEditUsesTheRoomsOwnPurseAndLadder() {
    var pack = RoomsTestSupport.cricket();
    var rules =
        new PlayerRowRules(
            java.util.Set.of("BAT"),
            java.util.Set.of(),
            PriceRule.of(100, List.of(new PresetDto.IncrementStep(0, 10))),
            "Room");

    assertThat(
            rules.check(new PlayerInput("Ann", "Chile", "BAT", List.of(), "90", "S", null)).valid())
        .isTrue();
    assertThat(
            rules.check(new PlayerInput("Ann", "Chile", "BAT", List.of(), "95", "S", null)).valid())
        .isFalse();
    assertThat(
            rules
                .check(new PlayerInput("Ann", "Chile", "BAT", List.of(), "110", "S", null))
                .valid())
        .isFalse();
    assertThat(pack).isNotNull();
  }

  @Test
  void theLadderBandThatHoldsThePriceDecidesTheStep() {
    var rule =
        PriceRule.of(
            1000, List.of(new PresetDto.IncrementStep(0, 5), new PresetDto.IncrementStep(100, 25)));

    assertThat(rule.accepts(95)).isTrue();
    assertThat(rule.accepts(100)).isTrue();
    assertThat(rule.accepts(105)).isFalse();
    assertThat(rule.accepts(125)).isTrue();
    assertThat(rule.accepts(1000)).isTrue();
    assertThat(rule.accepts(1025)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("packs")
  void lengthCapsApplyToNameCountrySetAndProviderId(Case c) {
    String long61 = "x".repeat(61);
    var in =
        new PlayerInput(
            long61,
            "y".repeat(41),
            c.role(),
            List.of(),
            c.price(),
            "z".repeat(41),
            "id with space");

    var result = rules(c).check(in);

    assertThat(fields(result)).containsExactlyInAnyOrder("name", "country", "set", "provider_id");
    assertThat(
            rules(c)
                .check(
                    new PlayerInput(
                        "x".repeat(60),
                        "y".repeat(40),
                        c.role(),
                        List.of(),
                        c.price(),
                        "z".repeat(40),
                        "a".repeat(64)))
                .valid())
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("packs")
  void markupOnlyTextIsEmptyAfterSanitisingAndRejected(Case c) {
    var in =
        new PlayerInput("<script></script>", "Chile", c.role(), List.of(), c.price(), "S", null);

    var result = rules(c).check(in);

    assertThat(fields(result)).containsExactly("name");
    assertThat(result.errors().get(0).message()).isEqualTo("Required.");
  }

  @ParameterizedTest
  @MethodSource("packs")
  void moreThanTenTagsIsRejected(Case c) {
    var in =
        new PlayerInput(
            "Ann",
            "Chile",
            c.role(),
            List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"),
            c.price(),
            "S",
            null);

    assertThat(fields(rules(c).check(in))).containsExactly("tags");
  }

  @ParameterizedTest
  @MethodSource("packs")
  void everyBrokenFieldOfOneRowIsReported(Case c) {
    var in = new PlayerInput("", "", "NOPE", List.of("nope"), "-1", "", null);

    assertThat(fields(rules(c).check(in)))
        .containsExactlyInAnyOrder("name", "country", "set", "role", "tags", "base_price");
    assertThat(row(c)).isNotNull();
  }

  @Test
  void roleAndTagMessagesNameThePackAndListTheKeysInPackOrder() {
    var cricket = RoomsTestSupport.cricket();
    var rules = PlayerRowRules.forPack(cricket);

    var result =
        rules.check(new PlayerInput("Ann", "Chile", "BATTER", List.of("local"), "20", "S", null));

    assertThat(result.errors().get(0).message())
        .isEqualTo("Not a " + cricket.display().name() + " role. Use BAT, BOWL, AR or WK.");
    assertThat(result.errors().get(1).message())
        .isEqualTo("\"local\" is not a " + cricket.display().name() + " tag.");
  }
}
