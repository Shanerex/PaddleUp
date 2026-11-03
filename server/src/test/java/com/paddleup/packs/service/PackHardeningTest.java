package com.paddleup.packs.service;

import static com.paddleup.packs.PackTestSupport.STUB_DIR;
import static com.paddleup.packs.PackTestSupport.loadFailure;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.packs.exception.PackLoadException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Malformed packs: each one is the stub pack with a single defect, and must end startup with a
 * {@link PackLoadException} naming the defect, never a bare exception.
 */
class PackHardeningTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @TempDir Path dir;

  private PackLoadException failWith(Consumer<ObjectNode> defect) throws Exception {
    var root = (ObjectNode) mapper.readTree(STUB_DIR.resolve("stub-football.json").toFile());
    defect.accept(root);
    Files.writeString(dir.resolve("pack.json"), mapper.writeValueAsString(root));
    return loadFailure(dir);
  }

  private static ObjectNode at(ObjectNode root, String pointer) {
    JsonNode node = root.at(pointer);
    assertThat(node.isObject()).as("fixture node %s", pointer).isTrue();
    return (ObjectNode) node;
  }

  @ParameterizedTest(name = "null in {0}")
  @CsvSource({"/roles", "/tags", "/restrictedCategories", "/presets", "/statSchema"})
  void nullListElementIsAViolationNotACrash(String list) throws Exception {
    var failure = failWith(root -> ((ArrayNode) root.at(list)).addNull());

    assertThat(failure.violations()).hasSize(1);
    assertThat(failure.violations().get(0).check()).isEqualTo("invalid-section");
    assertThat(failure.getMessage()).contains("pack.json", "stub-football", list.substring(1));
  }

  @Test
  void nullTerminologyValueIsAViolationNotACrash() throws Exception {
    var failure = failWith(root -> at(root, "/terminology").putNull("squad"));

    assertThat(failure.violations()).hasSize(1);
    assertThat(failure.getMessage()).contains("terminology", "squad");
  }

  @Test
  void statWithoutIdIsAViolationNotACrash() throws Exception {
    var failure = failWith(root -> at(root, "/statSchema/0").remove("id"));

    assertThat(failure.violations()).hasSize(1);
    assertThat(failure.getMessage()).contains("statSchema", "id");
  }

  @ParameterizedTest(name = "preset without {0}")
  @CsvSource({
    "label",
    "purse",
    "currencyCode",
    "displayUnit",
    "incrementLadder",
    "squadMin",
    "squadMax",
    "retention"
  })
  void presetMissingARequiredFieldNamesPresetAndField(String field) throws Exception {
    var failure = failWith(root -> at(root, "/presets/0").remove(field));

    assertThat(failure.violations()).extracting("check").contains("invalid-section");
    assertThat(failure.getMessage()).contains("standard", field);
  }

  @ParameterizedTest(name = "{0} missing {1}")
  @CsvSource({
    "/presets/0/displayUnit, divisor",
    "/presets/0/displayUnit, label",
    "/presets/0/incrementLadder/0, step",
    "/presets/0/retention, rtmCards",
    "/restrictedCategories/0, max",
    "/restrictedCategories/0, tag",
    "/roles/0, label",
    "/statSchema/0, kind",
    "/appearanceRule, atLeast"
  })
  void nestedRequiredFieldMissingIsAViolation(String pointer, String field) throws Exception {
    var failure = failWith(root -> at(root, pointer).remove(field));

    assertThat(failure.violations()).extracting("check").contains("invalid-section");
    assertThat(failure.getMessage()).contains(field);
  }

  @ParameterizedTest(name = "rule {0} missing {1}")
  @CsvSource({
    "/defaultRuleSet/rules/1/tiers/0, points",
    "/defaultRuleSet/rules/1/tiers/0, atLeast",
    "/defaultRuleSet/rules/2/bands/0, from",
    "/defaultRuleSet/rules/2/bands/0, to",
    "/defaultRuleSet/rules/2/bands/0, points",
    "/defaultRuleSet/rules/2/minVolume, atLeast",
    "/defaultRuleSet/rules/2/minVolume, stat"
  })
  void ruleMissingANestedFieldIsAViolation(String pointer, String field) throws Exception {
    var failure = failWith(root -> at(root, pointer).remove(field));

    assertThat(failure.violations()).extracting("check").containsExactly("invalid-rule");
    assertThat(failure.getMessage()).contains(field);
  }

  @ParameterizedTest(name = "rule with empty {0}")
  @CsvSource({"1, tiers", "2, bands"})
  void ruleWithAnEmptyLadderIsAViolation(int rule, String field) throws Exception {
    var failure =
        failWith(
            root ->
                ((ArrayNode) root.at("/defaultRuleSet/rules/" + rule + "/" + field)).removeAll());

    assertThat(failure.violations()).extracting("check").containsExactly("invalid-rule");
    assertThat(failure.getMessage()).contains(field);
  }

  @ParameterizedTest(name = "{0} {1} = {2} reports {3}")
  @CsvSource({
    "/presets/0/incrementLadder/0, step, 0, ladder-step",
    "/presets/0/incrementLadder/1, step, -5, ladder-step",
    "/presets/0/displayUnit, divisor, 0, display-divisor",
    "/presets/0/displayUnit, divisor, -1, display-divisor",
    "/presets/0, squadMin, -1, negative-squad-min",
    "/restrictedCategories/0, max, -1, negative-category-max"
  })
  void nonPositiveBoundIsAViolation(String pointer, String field, int value, String check)
      throws Exception {
    var failure = failWith(root -> at(root, pointer).put(field, value));

    assertThat(failure.violations()).extracting("check").contains(check);
    assertThat(failure.violations().stream().filter(v -> v.check().equals(check)))
        .allSatisfy(v -> assertThat(v.message()).containsAnyOf("standard", "foreign-cap"));
  }

  /** Stub retention gets tag limits, a slab per tag, so each defect below is a single edit. */
  private static void retentionWithSlabs(ObjectNode root, int guestLimit, int guestPrices) {
    var retention = at(root, "/presets/0/retention");
    retention.putObject("tagLimits").put("local", 1).put("foreign", guestLimit);
    var slabs = retention.putArray("slabs");
    slabs.addObject().put("tag", "local").putArray("prices").add(100);
    var foreign = slabs.addObject().put("tag", "foreign").putArray("prices");
    for (int i = 0; i < guestPrices; i++) {
      foreign.add(100);
    }
  }

  @Test
  void slabWithFewerPricesThanItsTagLimitIsAViolation() throws Exception {
    var failure = failWith(root -> retentionWithSlabs(root, 2, 1));

    assertThat(failure.violations()).hasSize(1);
    var violation = failure.violations().get(0);
    assertThat(violation.check()).isEqualTo("retention-slab-short");
    assertThat(violation.message()).contains("standard", "foreign", "1", "2");
  }

  @Test
  void slabWithoutATagIsAViolationNotANullPointer() throws Exception {
    var failure =
        failWith(
            root -> {
              retentionWithSlabs(root, 1, 1);
              ((ObjectNode) root.at("/presets/0/retention/slabs/1")).remove("tag");
            });

    assertThat(failure.violations()).extracting("check").contains("retention-slab-no-tag");
    assertThat(failure.violations())
        .allSatisfy(v -> assertThat(v.message()).doesNotContain("NullPointer"));
  }

  @ParameterizedTest(name = "null in {0}")
  @CsvSource({
    "/presets/0/incrementLadder",
    "/presets/0/roleMinimums",
    "/presets/0/retention/tagLimits",
    "/presets/0/retention/slabs",
    "/presets/0/retention/slabs/0/prices"
  })
  void nullValueInPresetCollectionIsAViolationNotACrash(String pointer) throws Exception {
    var failure =
        failWith(
            root -> {
              retentionWithSlabs(root, 1, 1);
              var node = root.at(pointer);
              if (node instanceof ArrayNode array) {
                array.addNull();
              } else {
                ((ObjectNode) node).putNull("x");
              }
            });

    assertThat(failure.violations()).hasSize(1);
    assertThat(failure.violations().get(0).check()).isEqualTo("invalid-section");
    assertThat(failure.violations().get(0).message())
        .doesNotContain("NullPointer")
        .contains("standard");
  }

  @Test
  void parseAndValidationViolationsAreReportedTogether() throws Exception {
    var failure =
        failWith(
            root -> {
              at(root, "/presets/0/displayUnit").remove("divisor"); // parse violation
              at(root, "/defaultRuleSet/rules/0").put("stat", "tackles"); // unknown-stat
              root.put("adapterKey", "no-such-adapter"); // unknown-adapter-key
              ((ArrayNode) root.at("/roles")).add(NullNode.instance); // parse violation
            });

    assertThat(failure.violations())
        .extracting("check")
        .contains("invalid-section", "unknown-stat", "unknown-adapter-key");
    assertThat(failure.violations()).hasSize(4);
  }
}
