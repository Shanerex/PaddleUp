package com.paddleup.packs.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.packs.dto.AppearanceRuleDto;
import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.dto.RestrictedCategoryDto;
import com.paddleup.packs.dto.RoleDto;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.packs.dto.TagDto;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PackParserPackTest {

  private final com.fasterxml.jackson.databind.ObjectMapper mapper = PackParser.newMapper();
  private final PackParser parser = new PackParser(mapper);

  private JsonNode stub() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/packs/stub/stub-football.json")) {
      return mapper.readTree(in);
    }
  }

  @Test
  void everySectionEqualsTheFileValues() throws Exception {
    var result = parser.parsePack(stub(), "stub-football.json");

    assertThat(result.violations()).isEmpty();
    var pack = result.value();
    assertThat(pack.schemaVersion()).isEqualTo(1);
    assertThat(pack.id()).isEqualTo("stub-football");
    assertThat(pack.version()).isEqualTo("1.0.0");
    assertThat(pack.roles())
        .containsExactly(
            new RoleDto("gk", "Goalkeeper"),
            new RoleDto("def", "Defender"),
            new RoleDto("mid", "Midfielder"),
            new RoleDto("fwd", "Forward"));
    assertThat(pack.tags())
        .containsExactly(new TagDto("local", "Local"), new TagDto("foreign", "Foreign"));
    assertThat(pack.restrictedCategories())
        .containsExactly(new RestrictedCategoryDto("foreign-cap", "Foreign players", "foreign", 4));
    assertThat(pack.presets())
        .containsExactly(
            new PresetDto(
                "standard",
                "Standard league",
                100_000_000L,
                "EUR",
                new PresetDto.DisplayUnit("M", 1_000_000L),
                List.of(
                    new PresetDto.IncrementStep(0, 100_000),
                    new PresetDto.IncrementStep(5_000_000, 250_000)),
                11,
                18,
                Map.of("gk", 1, "def", 3, "mid", 3, "fwd", 2),
                new PresetDto.Retention(2, 1)));
    assertThat(pack.statSchema())
        .hasSize(6)
        .startsWith(
            new StatDto("goals", "Goals", "count", List.of("open-play", "penalty")),
            new StatDto("assists", "Assists", "count", List.of()));
    assertThat(pack.defaultRuleSet().rules())
        .extracting(RuleDto::id)
        .containsExactly(
            "assist-points",
            "save-bonus",
            "pass-accuracy",
            "keeper-saves",
            "penalty-goal",
            "appearance");
    assertThat(pack.defaultRuleSet().rules().get(1))
        .isEqualTo(
            new RuleDto.ThresholdBonus(
                "save-bonus",
                "saves",
                List.of(
                    new RuleDto.Tier(new BigDecimal("5"), new BigDecimal("2")),
                    new RuleDto.Tier(new BigDecimal("8"), new BigDecimal("4.5")))));
    assertThat(pack.appearanceRule())
        .isEqualTo(new AppearanceRuleDto("minutes", new BigDecimal("1")));
    assertThat(pack.adapterKey()).isEqualTo(Optional.empty());
    assertThat(pack.manualEntryOnly()).isTrue();
    assertThat(pack.terminology()).isEqualTo(Map.of("squad", "Matchday squad", "match", "Fixture"));
    assertThat(pack.aiDomainContext()).startsWith("A football league");
  }

  @Test
  void adapterKeyIsReadWhenPresent() throws Exception {
    var root = (ObjectNode) stub();
    root.put("adapterKey", "stub-feed");

    var pack = parser.parsePack(root, "stub-football.json").value();

    assertThat(pack.adapterKey()).contains("stub-feed");
    assertThat(pack.manualEntryOnly()).isFalse();
  }

  @Test
  void missingSectionIsARequiredSectionViolation() throws Exception {
    var root = (ObjectNode) stub();
    root.remove("tags");

    var result = parser.parsePack(root, "stub-football.json");

    assertThat(result.violations()).hasSize(1);
    var violation = result.violations().get(0);
    assertThat(violation.check()).isEqualTo("required-section");
    assertThat(violation.message()).contains("tags");
    assertThat(violation.packId()).isEqualTo("stub-football");
  }

  @Test
  void malformedSectionIsReportedAndOthersStillParse() throws Exception {
    var root = (ObjectNode) stub();
    root.put("presets", "not-a-list");
    root.putObject("appearanceRule").put("stat", 5).put("atLeast", "x");

    var result = parser.parsePack(root, "stub-football.json");

    assertThat(result.violations()).extracting("check").containsOnly("invalid-section");
    assertThat(result.violations()).hasSize(2);
    assertThat(result.value().roles()).hasSize(4);
  }
}
