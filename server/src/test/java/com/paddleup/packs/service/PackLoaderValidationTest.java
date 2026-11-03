package com.paddleup.packs.service;

import static com.paddleup.packs.PackTestSupport.loadFailure;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PackLoaderValidationTest {

  @Test
  void unsupportedSchemaVersionNamesTheVersionAndTheSupportedOnes() {
    var failure = loadFailure("schema-version-unsupported");

    assertThat(failure.violations()).hasSize(1);
    assertThat(failure.getMessage())
        .contains("pack.json", "stub-football", "7", "supported", "[1]");
  }

  @Test
  void samePackIdInTwoFilesWithTheSameVersionFailsNamingBothFiles() {
    var failure = loadFailure("duplicate-id-same-version");

    assertThat(failure.getMessage()).contains("a.json", "b.json", "stub-football");
  }

  @Test
  void samePackIdInTwoFilesWithDifferentVersionsFailsNamingBothFiles() {
    var failure = loadFailure("duplicate-id-different-version");

    assertThat(failure.getMessage()).contains("a.json", "b.json", "stub-football");
  }

  @Test
  void unknownRoleInARuleNamesFilePackAndRole() {
    var failure = loadFailure("unknown-role-in-rule");

    assertThat(failure.violations()).hasSize(1);
    assertThat(failure.violations().get(0).check()).isEqualTo("unknown-role");
    assertThat(failure.getMessage()).contains("pack.json", "stub-football", "striker");
  }

  @Test
  void unknownRoleInARoleMinimumNamesFilePackAndRole() {
    var failure = loadFailure("unknown-role-in-minimum");

    assertThat(failure.violations()).extracting("check").containsExactly("unknown-role");
    assertThat(failure.getMessage()).contains("pack.json", "stub-football", "coach");
  }

  @Test
  void roleMinimumsAboveSquadMinStateBothNumbers() {
    var failure = loadFailure("role-minimums-exceed-squad-min");

    assertThat(failure.violations())
        .extracting("check")
        .containsExactly("role-minimums-exceed-squad-min");
    assertThat(failure.getMessage()).contains("9", "8", "standard");
  }

  @Test
  void ruleStatOutsideTheStatSchemaNamesTheStat() {
    var failure = loadFailure("unknown-stat-in-rule");

    assertThat(failure.violations()).extracting("check").containsExactly("unknown-stat");
    assertThat(failure.getMessage()).contains("tackles", "assist-points");
  }

  @Test
  void eventTypeNotDeclaredOnItsStatNamesTheEventType() {
    var failure = loadFailure("unknown-event-type");

    assertThat(failure.violations()).extracting("check").containsExactly("unknown-event-type");
    assertThat(failure.getMessage()).contains("header", "goals", "penalty-goal");
  }
}
