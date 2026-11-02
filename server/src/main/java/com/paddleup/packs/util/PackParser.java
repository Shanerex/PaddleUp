package com.paddleup.packs.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.paddleup.packs.dto.AppearanceRuleDto;
import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.dto.RestrictedCategoryDto;
import com.paddleup.packs.dto.RoleDto;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.packs.dto.TagDto;
import com.paddleup.packs.exception.PackViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns pack JSON trees into dto records, one section and one rule at a time, so a broken piece
 * becomes a {@link PackViolation} instead of aborting the whole parse.
 */
public class PackParser {

  /** A parsed value plus the violations met while producing it; the value omits broken parts. */
  public record Parsed<T>(T value, List<PackViolation> violations) {
    public Parsed {
      violations = List.copyOf(violations);
    }
  }

  private final ObjectMapper mapper;

  public PackParser(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** A mapper that reads decimals exactly and rejects properties the dtos do not know. */
  public static ObjectMapper newMapper() {
    return JsonMapper.builder()
        .findAndAddModules()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
  }

  /**
   * Parses a whole pack. Each section is read on its own; a missing section is a {@code
   * required-section} violation and an unreadable one an {@code invalid-section} violation. The
   * returned pack holds empty values for sections that failed, so callers must check the violations
   * before trusting it.
   */
  public Parsed<PackDto> parsePack(JsonNode root, String file) {
    var violations = new ArrayList<PackViolation>();
    var packId = root.path("id").isTextual() ? root.get("id").asText() : "?";
    var in = new Section(root, file, packId, violations);

    var schemaVersion = in.<Integer>read("schemaVersion", type(Integer.class), 0);
    var id = in.<String>read("id", type(String.class), null);
    var version = in.<String>read("version", type(String.class), null);
    var roles = in.read("roles", list(RoleDto.class), List.<RoleDto>of());
    var tags = in.read("tags", list(TagDto.class), List.<TagDto>of());
    var categories =
        in.read(
            "restrictedCategories",
            list(RestrictedCategoryDto.class),
            List.<RestrictedCategoryDto>of());
    var presets = in.read("presets", list(PresetDto.class), List.<PresetDto>of());
    var stats = in.read("statSchema", list(StatDto.class), List.<StatDto>of());
    var appearance =
        in.<AppearanceRuleDto>read("appearanceRule", type(AppearanceRuleDto.class), null);
    var terminology =
        in.read(
            "terminology",
            mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class),
            Map.<String, String>of());
    var aiContext = in.<String>read("aiDomainContext", type(String.class), null);
    if (aiContext != null && aiContext.isBlank()) {
      violations.add(
          new PackViolation(
              file, packId, "required-section", "section 'aiDomainContext' is blank"));
    }

    RuleSetDto ruleSet = new RuleSetDto(List.of());
    if (in.present("defaultRuleSet")) {
      var node = root.get("defaultRuleSet");
      if (node.isObject() && node.has("rules")) {
        var rules = parseRules(node.get("rules"), file, packId);
        violations.addAll(rules.violations());
        ruleSet = rules.value();
      } else {
        violations.add(
            new PackViolation(
                file,
                packId,
                "invalid-section",
                "section 'defaultRuleSet' must be an object with a 'rules' array"));
      }
    }

    var adapterKey = Optional.<String>empty();
    var keyNode = root.get("adapterKey");
    if (keyNode != null && !keyNode.isNull()) {
      if (keyNode.isTextual() && !keyNode.asText().isBlank()) {
        adapterKey = Optional.of(keyNode.asText());
      } else {
        violations.add(
            new PackViolation(
                file,
                packId,
                "invalid-section",
                "section 'adapterKey' must be a non-blank string"));
      }
    }

    var pack =
        new PackDto(
            schemaVersion,
            id,
            version,
            roles,
            tags,
            categories,
            presets,
            stats,
            ruleSet,
            appearance,
            adapterKey,
            terminology,
            aiContext);
    return new Parsed<>(pack, violations);
  }

  private JavaType type(Class<?> type) {
    return mapper.constructType(type);
  }

  private <T> JavaType list(Class<T> type) {
    return mapper.getTypeFactory().constructCollectionType(List.class, type);
  }

  /** Reads named sections of one pack file, collecting violations. */
  private final class Section {
    private final JsonNode root;
    private final String file;
    private final String packId;
    private final List<PackViolation> violations;

    Section(JsonNode root, String file, String packId, List<PackViolation> violations) {
      this.root = root;
      this.file = file;
      this.packId = packId;
      this.violations = violations;
    }

    boolean present(String name) {
      var node = root.get(name);
      if (node == null || node.isNull()) {
        violations.add(
            new PackViolation(file, packId, "required-section", "missing section '" + name + "'"));
        return false;
      }
      return true;
    }

    @SuppressWarnings("unchecked")
    <T> T read(String name, JavaType javaType, T fallback) {
      if (!present(name)) {
        return fallback;
      }
      try {
        return (T) mapper.readerFor(javaType).readValue(root.get(name));
      } catch (Exception e) {
        violations.add(
            new PackViolation(
                file,
                packId,
                "invalid-section",
                "section '%s' cannot be read: %s".formatted(name, firstLine(e.getMessage()))));
        return fallback;
      }
    }
  }

  /** Parses a JSON array of rules; each rule that fails is reported and left out of the set. */
  public Parsed<RuleSetDto> parseRules(JsonNode rules, String file, String packId) {
    var violations = new ArrayList<PackViolation>();
    var parsed = new ArrayList<RuleDto>();
    if (rules == null || !rules.isArray()) {
      violations.add(new PackViolation(file, packId, "invalid-rule", "rules must be a JSON array"));
      return new Parsed<>(new RuleSetDto(parsed), violations);
    }
    for (JsonNode node : rules) {
      var unknown = unknownType(node);
      if (unknown != null) {
        violations.add(
            new PackViolation(
                file,
                packId,
                "unknown-rule-type",
                "rule '%s' has unknown type '%s'; supported: %s"
                    .formatted(node.path("id").asText("?"), unknown, RuleDto.TYPES)));
        continue;
      }
      try {
        var rule = mapper.treeToValue(node, RuleDto.class);
        var missing = missingField(rule);
        if (missing != null) {
          violations.add(
              new PackViolation(
                  file,
                  packId,
                  "invalid-rule",
                  "rule '%s' is missing '%s'".formatted(node.path("id").asText("?"), missing)));
          continue;
        }
        parsed.add(rule);
      } catch (Exception e) {
        violations.add(
            new PackViolation(
                file,
                packId,
                "invalid-rule",
                "rule '%s' cannot be read: %s"
                    .formatted(node.path("id").asText("?"), firstLine(e.getMessage()))));
      }
    }
    return new Parsed<>(new RuleSetDto(parsed), violations);
  }

  /** Names the first required field of the rule (or a nested rule) that is absent, or null. */
  private static String missingField(RuleDto rule) {
    return switch (rule) {
      case RuleDto.PerUnit r -> anyNull("id", r.id(), "stat", r.stat(), "points", r.points());
      case RuleDto.ThresholdBonus r -> anyNull("id", r.id(), "stat", r.stat());
      case RuleDto.BandedRate r ->
          anyNull(
              "id", r.id(),
              "numerator", r.numerator(),
              "denominator", r.denominator(),
              "scale", r.scale(),
              "minVolume", r.minVolume());
      case RuleDto.RoleConditional r -> {
        var own = anyNull("id", r.id(), "rule", r.rule());
        yield own != null ? own : missingField(r.rule());
      }
      case RuleDto.EventTypeBonus r ->
          anyNull("id", r.id(), "stat", r.stat(), "eventType", r.eventType(), "points", r.points());
      case RuleDto.AppearanceBonus r -> anyNull("id", r.id(), "points", r.points());
    };
  }

  private static String anyNull(Object... namesAndValues) {
    for (int i = 0; i < namesAndValues.length; i += 2) {
      if (namesAndValues[i + 1] == null) {
        return (String) namesAndValues[i];
      }
    }
    return null;
  }

  /** Returns the first type value that is not a known primitive, looking inside nested rules. */
  private static String unknownType(JsonNode rule) {
    if (rule == null || !rule.isObject()) {
      return null;
    }
    var type = rule.path("type");
    if (type.isTextual() && !RuleDto.TYPES.contains(type.asText())) {
      return type.asText();
    }
    if (RuleDto.ROLE_CONDITIONAL.equals(type.asText())) {
      return unknownType(rule.get("rule"));
    }
    return null;
  }

  private static String firstLine(String message) {
    if (message == null) {
      return "invalid value";
    }
    int end = message.indexOf('\n');
    return end < 0 ? message : message.substring(0, end);
  }
}
