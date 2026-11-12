package com.paddleup.packs.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.packs.dto.RuleDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PackParserRulesTest {

  private final ObjectMapper mapper = PackParser.newMapper();
  private final PackParser parser = new PackParser(mapper);

  private JsonNode json(String text) throws Exception {
    return mapper.readTree(text);
  }

  @Test
  void eachPrimitiveParsesToItsRecord() throws Exception {
    var rules =
        json(
            """
            [
              {"type":"per-unit","id":"r1","stat":"s1","points":1.5},
              {"type":"threshold-bonus","id":"r2","stat":"s1","tiers":[{"atLeast":3,"points":4},{"atLeast":5,"points":8}]},
              {"type":"banded-rate","id":"r3","numerator":"s1","denominator":"s2","scale":100,
               "minVolume":{"stat":"s2","atLeast":10},
               "bands":[{"from":0,"to":50,"points":-2},{"from":50,"to":100,"points":2}]},
              {"type":"role-conditional","id":"r4","roles":["ro1"],
               "rule":{"type":"per-unit","id":"r4a","stat":"s2","points":2}},
              {"type":"event-type-bonus","id":"r5","stat":"s1","eventType":"e1","points":8},
              {"type":"appearance-bonus","id":"r6","points":4}
            ]
            """);

    var result = parser.parseRules(rules, "p.json", "p1");

    assertThat(result.violations()).isEmpty();
    assertThat(result.value().rules())
        .containsExactly(
            new RuleDto.PerUnit("r1", "s1", new BigDecimal("1.5")),
            new RuleDto.ThresholdBonus(
                "r2",
                "s1",
                List.of(
                    new RuleDto.Tier(new BigDecimal("3"), new BigDecimal("4")),
                    new RuleDto.Tier(new BigDecimal("5"), new BigDecimal("8")))),
            new RuleDto.BandedRate(
                "r3",
                "s1",
                "s2",
                new BigDecimal("100"),
                new RuleDto.MinVolume("s2", new BigDecimal("10")),
                List.of(
                    new RuleDto.Band(
                        new BigDecimal("0"), new BigDecimal("50"), new BigDecimal("-2")),
                    new RuleDto.Band(
                        new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("2")))),
            new RuleDto.RoleConditional(
                "r4", List.of("ro1"), new RuleDto.PerUnit("r4a", "s2", new BigDecimal("2"))),
            new RuleDto.EventTypeBonus("r5", "s1", "e1", new BigDecimal("8")),
            new RuleDto.AppearanceBonus("r6", new BigDecimal("4")));
  }

  @Test
  void unknownTypeIsAViolationNamingIt() throws Exception {
    var rules =
        json(
            """
            [
              {"type":"per-unit","id":"r1","stat":"s1","points":1},
              {"type":"quadratic-bonus","id":"r2","stat":"s1","points":1}
            ]
            """);

    var result = parser.parseRules(rules, "p.json", "p1");

    assertThat(result.violations()).hasSize(1);
    var violation = result.violations().get(0);
    assertThat(violation.file()).isEqualTo("p.json");
    assertThat(violation.packId()).isEqualTo("p1");
    assertThat(violation.check()).isEqualTo("unknown-rule-type");
    assertThat(violation.message()).contains("quadratic-bonus", "r2");
    assertThat(result.value().rules()).hasSize(1);
  }

  @Test
  void unknownTypeInsideARoleConditionalIsReported() throws Exception {
    var rules =
        json(
            """
            [{"type":"role-conditional","id":"r4","roles":["ro1"],
              "rule":{"type":"nope","id":"x"}}]
            """);

    var result = parser.parseRules(rules, "p.json", "p1");

    assertThat(result.violations()).extracting("check").containsExactly("unknown-rule-type");
    assertThat(result.violations().get(0).message()).contains("nope");
  }

  @Test
  void malformedRuleBecomesAViolationNotAnAbort() throws Exception {
    var rules = json("[{\"type\":\"per-unit\",\"id\":\"r1\",\"stat\":\"s1\",\"points\":\"abc\"}]");

    var result = parser.parseRules(rules, "p.json", "p1");

    assertThat(result.violations()).extracting("check").containsExactly("invalid-rule");
  }

  @Test
  void missingRequiredFieldIsAViolation() throws Exception {
    var rules =
        json(
            """
            [
              {"type":"banded-rate","id":"r3","numerator":"s1","denominator":"s2","scale":100,
               "bands":[]},
              {"type":"role-conditional","id":"r4","roles":["ro1"]}
            ]
            """);

    var result = parser.parseRules(rules, "p.json", "p1");

    assertThat(result.violations())
        .extracting("check")
        .containsExactly("invalid-rule", "invalid-rule");
    assertThat(result.violations().get(0).message()).contains("minVolume");
    assertThat(result.violations().get(1).message()).contains("rule");
  }

  @Test
  void labelRoundTripsOnEveryPrimitiveAndItsAbsenceIsAccepted() throws Exception {
    var rules =
        json(
            """
            [
              {"type":"per-unit","id":"r1","label":"L1","stat":"s1","points":1},
              {"type":"threshold-bonus","id":"r2","label":"L2","stat":"s1","tiers":[{"atLeast":3,"points":4}]},
              {"type":"banded-rate","id":"r3","label":"L3","numerator":"s1","denominator":"s2","scale":1,
               "minVolume":{"stat":"s2","atLeast":10},"bands":[{"from":0,"to":50,"points":-2}]},
              {"type":"role-conditional","id":"r4","label":"L4","roles":["ro1"],
               "rule":{"type":"per-unit","id":"r4a","stat":"s2","points":2}},
              {"type":"event-type-bonus","id":"r5","label":"L5","stat":"s1","eventType":"e1","points":8},
              {"type":"appearance-bonus","id":"r6","label":"L6","points":4},
              {"type":"per-unit","id":"r7","stat":"s1","points":1}
            ]
            """);

    var result = parser.parseRules(rules, "p.json", "p1");

    assertThat(result.violations()).isEmpty();
    assertThat(result.value().rules())
        .extracting(RuleDto::label)
        .containsExactly("L1", "L2", "L3", "L4", "L5", "L6", null);
    var written = mapper.writeValueAsString(result.value());
    var back = mapper.readValue(written, com.paddleup.packs.dto.RuleSetDto.class);
    assertThat(back).isEqualTo(result.value());
  }
}
