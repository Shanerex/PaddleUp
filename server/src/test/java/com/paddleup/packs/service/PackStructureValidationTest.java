package com.paddleup.packs.service;

import static com.paddleup.packs.PackTestSupport.loadFailure;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PackStructureValidationTest {

  @ParameterizedTest(name = "{0} reports {1} naming {2}")
  @CsvSource({
    "required-section-missing, required-section, tags",
    "duplicate-id-role, duplicate-id, gk",
    "squad-bounds, squad-bounds, standard",
    "negative-role-minimum, negative-role-minimum, def",
    "ladder-order, ladder-order, standard",
    "category-unknown-tag, category-unknown-tag, visitor",
    "category-max, category-max, foreign-cap",
    "appearance-unknown-stat, appearance-unknown-stat, shifts"
  })
  void eachStructuralCheckNamesTheCheckAndTheOffendingId(
      String fixture, String check, String offendingId) {
    var failure = loadFailure(fixture);

    assertThat(failure.violations()).hasSize(1);
    var violation = failure.violations().get(0);
    assertThat(violation.check()).isEqualTo(check);
    assertThat(violation.file()).isEqualTo("pack.json");
    assertThat(violation.message()).contains(offendingId);
    assertThat(failure.getMessage()).contains(check, offendingId);
  }

  @Test
  void threeViolationsAreReportedInOneError() {
    var failure = loadFailure("three-violations");

    assertThat(failure.violations())
        .extracting("check")
        .containsExactlyInAnyOrder("unknown-role", "unknown-stat", "ladder-order");
    assertThat(failure.getMessage()).contains("3 violations");
  }
}
