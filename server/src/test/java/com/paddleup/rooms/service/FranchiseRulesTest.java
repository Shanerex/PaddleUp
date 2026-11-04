package com.paddleup.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.rooms.exception.RoomsRequestException;
import com.paddleup.rooms.model.FranchiseColour;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FranchiseRulesTest {

  private static void rejects(Runnable action, String field) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            RoomsRequestException.class, e -> assertThat(e.field()).isEqualTo(field));
  }

  @Test
  void aNameOfTwoToThirtyCharactersIsAccepted() {
    assertThat(FranchiseRules.cleanName("AB")).isEqualTo("AB");
    assertThat(FranchiseRules.cleanName("x".repeat(30))).hasSize(30);
  }

  @Test
  void aNameOfOneOrThirtyOneCharactersIsRejected() {
    rejects(() -> FranchiseRules.cleanName("A"), "name");
    rejects(() -> FranchiseRules.cleanName("x".repeat(31)), "name");
    rejects(() -> FranchiseRules.cleanName(null), "name");
    rejects(() -> FranchiseRules.cleanName("   "), "name");
  }

  @Test
  void markupIsStrippedFromANameBeforeItIsLengthChecked() {
    assertThat(FranchiseRules.cleanName("<b>Kings</b><script>alert(1)</script>"))
        .isEqualTo("Kings");
    assertThat(FranchiseRules.cleanName("  Super   Kings ")).isEqualTo("Super Kings");
    rejects(() -> FranchiseRules.cleanName("<i>a</i>"), "name");
  }

  @Test
  void namesDifferingOnlyByCaseHaveTheSameKey() {
    assertThat(FranchiseRules.nameKey("Super Kings"))
        .isEqualTo(FranchiseRules.nameKey("SUPER kings"));
    assertThat(FranchiseRules.nameKey("Kings")).isNotEqualTo(FranchiseRules.nameKey("King"));
  }

  @Test
  void everyOneOfTheTwelveColoursIsAccepted() {
    for (FranchiseColour colour : FranchiseColour.values()) {
      assertThat(FranchiseRules.cleanColour(colour.key())).isEqualTo(colour.key());
    }
    assertThat(FranchiseColour.values()).hasSize(12);
    assertThat(FranchiseRules.cleanColour(" Crimson ")).isEqualTo("crimson");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "purple", "#ff0000", "crimson;"})
  void anyOtherColourIsRejected(String colour) {
    rejects(() -> FranchiseRules.cleanColour(colour), "colour");
    rejects(() -> FranchiseRules.cleanColour(null), "colour");
  }

  @Test
  void aMonogramOfOneToThreeCharactersIsAccepted() {
    assertThat(FranchiseRules.cleanMonogram("A")).isEqualTo("A");
    assertThat(FranchiseRules.cleanMonogram("ABC")).isEqualTo("ABC");
  }

  @Test
  void aMonogramOverThreeCharactersOrEmptyOrWithSpacesIsRejected() {
    rejects(() -> FranchiseRules.cleanMonogram("ABCD"), "monogram");
    rejects(() -> FranchiseRules.cleanMonogram(""), "monogram");
    rejects(() -> FranchiseRules.cleanMonogram(null), "monogram");
    rejects(() -> FranchiseRules.cleanMonogram("A B"), "monogram");
    rejects(() -> FranchiseRules.cleanMonogram("<b></b>"), "monogram");
  }

  @Test
  void cleanChecksAllThreeFields() {
    var clean = FranchiseRules.clean("<b>Kings</b>", "SKY", "KG");
    assertThat(clean.name()).isEqualTo("Kings");
    assertThat(clean.colour()).isEqualTo("sky");
    assertThat(clean.monogram()).isEqualTo("KG");
    rejects(() -> FranchiseRules.clean("Kings", "nope", "KG"), "colour");
  }
}
