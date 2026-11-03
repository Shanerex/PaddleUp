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
import java.util.Set;

/**
 * Turns pack JSON trees into dto records, one section and one rule at a time, so a broken piece
 * becomes a {@link PackViolation} instead of aborting the whole parse.
 */
public class PackParser {

  /**
   * A parsed value plus the violations met while producing it; the value omits broken parts. {@code
   * partialSections} names the pack sections that lost an entry or all of it, so later checks can
   * skip what they would wrongly report against the gap.
   */
  public record Parsed<T>(T value, List<PackViolation> violations, Set<String> partialSections) {
    public Parsed {
      violations = List.copyOf(violations);
      partialSections = Set.copyOf(partialSections);
    }

    Parsed(T value, List<PackViolation> violations) {
      this(value, violations, Set.of());
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
    var roles = in.track("roles", () -> in.readEntries("roles", RoleDto.class, LABELLED));
    var tags = in.track("tags", () -> in.readEntries("tags", TagDto.class, LABELLED));
    var categories =
        in.track(
            "restrictedCategories",
            () ->
                in.readEntries(
                    "restrictedCategories",
                    RestrictedCategoryDto.class,
                    Shape.of("id", "label", "tag", "max")));
    var presets = in.track("presets", () -> in.readEntries("presets", PresetDto.class, PRESET));
    var stats =
        in.track(
            "statSchema",
            () -> in.readEntries("statSchema", StatDto.class, Shape.of("id", "label", "kind")));
    var appearance =
        in.track(
            "appearanceRule",
            () -> in.readEntry("appearanceRule", AppearanceRuleDto.class, APPEARANCE));
    var terminology = in.readTerminology();
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
    return new Parsed<>(pack, violations, in.partial);
  }

  private JavaType type(Class<?> type) {
    return mapper.constructType(type);
  }

  private static final Shape LABELLED = Shape.of("id", "label");
  private static final Shape APPEARANCE = Shape.of("stat", "atLeast");
  private static final Shape PRESET =
      Shape.of(
              "id",
              "label",
              "purse",
              "currencyCode",
              "displayUnit",
              "incrementLadder",
              "squadMin",
              "squadMax",
              "retention")
          .object("displayUnit", Shape.of("label", "divisor"))
          .each("incrementLadder", Shape.of("from", "step"))
          .object("retention", Shape.of("maxRetentions", "rtmCards"));

  /**
   * The keys a JSON object must carry (present and not null), with the shapes of its nested objects
   * and of the entries of its arrays, which must not be empty.
   */
  private record Shape(List<String> keys, Map<String, Shape> objects, Map<String, Shape> arrays) {

    static Shape of(String... keys) {
      return new Shape(List.of(keys), Map.of(), Map.of());
    }

    Shape object(String key, Shape shape) {
      var all = new java.util.HashMap<>(objects);
      all.put(key, shape);
      return new Shape(keys, all, arrays);
    }

    Shape each(String key, Shape shape) {
      var all = new java.util.HashMap<>(arrays);
      all.put(key, shape);
      return new Shape(keys, objects, all);
    }

    /** Describes the first thing wrong with the node as a phrase after its name, or null. */
    String problem(JsonNode node) {
      return problem(node, "");
    }

    private String problem(JsonNode node, String path) {
      for (String key : keys) {
        var value = node.get(key);
        if (value == null || value.isNull()) {
          return "is missing '%s%s'".formatted(path, key);
        }
      }
      for (var nested : objects.entrySet()) {
        var at = path + nested.getKey();
        var value = node.get(nested.getKey());
        if (value == null || !value.isObject()) {
          return "has '%s' that is not an object".formatted(at);
        }
        var problem = nested.getValue().problem(value, at + ".");
        if (problem != null) {
          return problem;
        }
      }
      for (var nested : arrays.entrySet()) {
        var at = path + nested.getKey();
        var value = node.get(nested.getKey());
        if (value == null || !value.isArray() || value.isEmpty()) {
          return "has '%s' that is not a non-empty array".formatted(at);
        }
        for (int i = 0; i < value.size(); i++) {
          if (!value.get(i).isObject()) {
            return "has '%s[%d]' that is not an object".formatted(at, i);
          }
          var problem = nested.getValue().problem(value.get(i), "%s[%d].".formatted(at, i));
          if (problem != null) {
            return problem;
          }
        }
      }
      return null;
    }
  }

  /** Reads named sections of one pack file, collecting violations. */
  private final class Section {
    private final JsonNode root;
    private final String file;
    private final String packId;
    private final List<PackViolation> violations;
    private final Set<String> partial = new java.util.TreeSet<>();

    Section(JsonNode root, String file, String packId, List<PackViolation> violations) {
      this.root = root;
      this.file = file;
      this.packId = packId;
      this.violations = violations;
    }

    /** Performs a section read and records the section as partial when it added a violation. */
    <T> T track(String name, java.util.function.Supplier<T> read) {
      int before = violations.size();
      var value = read.get();
      if (violations.size() > before) {
        partial.add(name);
      }
      return value;
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

    /**
     * Reads a section that is an array of objects one entry at a time, so a broken entry is
     * reported by position and id and left out instead of discarding its siblings.
     */
    <T> List<T> readEntries(String name, Class<T> type, Shape shape) {
      if (!present(name)) {
        return List.of();
      }
      var node = root.get(name);
      if (!node.isArray()) {
        violations.add(
            new PackViolation(
                file, packId, "invalid-section", "section '" + name + "' must be an array"));
        return List.of();
      }
      var entries = new ArrayList<T>();
      for (int i = 0; i < node.size(); i++) {
        var entry = readOne(node.get(i), "%s[%d]".formatted(name, i), type, shape);
        if (entry != null) {
          entries.add(entry);
        }
      }
      return entries;
    }

    /** Reads a section that is one object; null when it is missing or broken. */
    <T> T readEntry(String name, Class<T> type, Shape shape) {
      return present(name) ? readOne(root.get(name), name, type, shape) : null;
    }

    private <T> T readOne(JsonNode node, String at, Class<T> type, Shape shape) {
      if (!node.isObject()) {
        violations.add(
            new PackViolation(file, packId, "invalid-section", at + " must be an object"));
        return null;
      }
      var name =
          node.path("id").isTextual() ? "%s ('%s')".formatted(at, node.get("id").asText()) : at;
      var problem = shape.problem(node);
      if (problem != null) {
        violations.add(new PackViolation(file, packId, "invalid-section", name + " " + problem));
        return null;
      }
      try {
        return mapper.treeToValue(node, type);
      } catch (Exception e) {
        violations.add(
            new PackViolation(
                file,
                packId,
                "invalid-section",
                "%s cannot be read: %s".formatted(name, firstLine(e.getMessage()))));
        return null;
      }
    }

    /** Reads the terminology map; every value must be a string. */
    Map<String, String> readTerminology() {
      if (!present("terminology")) {
        return Map.of();
      }
      var node = root.get("terminology");
      if (!node.isObject()) {
        violations.add(
            new PackViolation(
                file, packId, "invalid-section", "section 'terminology' must be an object"));
        return Map.of();
      }
      var terms = new java.util.LinkedHashMap<String, String>();
      node.fields()
          .forEachRemaining(
              field -> {
                if (field.getValue().isTextual()) {
                  terms.put(field.getKey(), field.getValue().asText());
                } else {
                  violations.add(
                      new PackViolation(
                          file,
                          packId,
                          "invalid-section",
                          "terminology '%s' must be a string".formatted(field.getKey())));
                }
              });
      return terms;
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
      case RuleDto.ThresholdBonus r -> {
        var own = anyNull("id", r.id(), "stat", r.stat());
        if (own == null && r.tiers().isEmpty()) {
          own = "tiers";
        }
        for (var tier : r.tiers()) {
          own =
              own != null
                  ? own
                  : anyNull("tiers.atLeast", tier.atLeast(), "tiers.points", tier.points());
        }
        yield own;
      }
      case RuleDto.BandedRate r -> {
        var own =
            anyNull(
                "id", r.id(),
                "numerator", r.numerator(),
                "denominator", r.denominator(),
                "scale", r.scale(),
                "minVolume", r.minVolume());
        if (own == null) {
          own =
              anyNull(
                  "minVolume.stat",
                  r.minVolume().stat(),
                  "minVolume.atLeast",
                  r.minVolume().atLeast());
        }
        if (own == null && r.bands().isEmpty()) {
          own = "bands";
        }
        for (var band : r.bands()) {
          own =
              own != null
                  ? own
                  : anyNull(
                      "bands.from",
                      band.from(),
                      "bands.to",
                      band.to(),
                      "bands.points",
                      band.points());
        }
        yield own;
      }
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
