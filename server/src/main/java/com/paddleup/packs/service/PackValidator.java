package com.paddleup.packs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.dto.RestrictedCategoryDto;
import com.paddleup.packs.dto.RoleDto;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.packs.dto.TagDto;
import com.paddleup.packs.exception.PackViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The checks applied to a pack after it parsed. Each violation carries a stable check code. */
class PackValidator {

  static final Set<Integer> SUPPORTED_SCHEMA_VERSIONS = Set.of(1);

  /** Rejects a file whose {@code schemaVersion} is present but not supported. */
  List<PackViolation> checkSchemaVersion(JsonNode tree, String file) {
    var node = tree.get("schemaVersion");
    if (node == null || node.isNull()) {
      return List.of(); // the parser reports the missing section
    }
    if (node.isInt() && SUPPORTED_SCHEMA_VERSIONS.contains(node.intValue())) {
      return List.of();
    }
    var packId = tree.path("id").asText("?");
    return List.of(
        new PackViolation(
            file,
            packId,
            "unsupported-schema-version",
            "schemaVersion %s is not supported; supported: %s"
                .formatted(node.asText(), SUPPORTED_SCHEMA_VERSIONS)));
  }

  /** Applies every check to a pack that parsed without violations. */
  List<PackViolation> validate(PackDto pack, String file) {
    var violations = new ArrayList<PackViolation>();
    checkReferences(pack, file, violations);
    checkStructure(pack, file, violations);
    return violations;
  }

  /** Structural checks: unique ids, sane bounds, ordered ladders, valid tag and stat references. */
  private void checkStructure(PackDto pack, String file, List<PackViolation> out) {
    uniqueIds(pack, file, "roles", pack.roles().stream().map(RoleDto::id).toList(), out);
    uniqueIds(pack, file, "tags", pack.tags().stream().map(TagDto::id).toList(), out);
    uniqueIds(
        pack,
        file,
        "restrictedCategories",
        pack.restrictedCategories().stream().map(RestrictedCategoryDto::id).toList(),
        out);
    uniqueIds(pack, file, "presets", pack.presets().stream().map(PresetDto::id).toList(), out);
    uniqueIds(pack, file, "statSchema", pack.statSchema().stream().map(StatDto::id).toList(), out);
    uniqueIds(
        pack,
        file,
        "defaultRuleSet",
        flatten(pack.defaultRuleSet().rules()).stream().map(RuleDto::id).toList(),
        out);

    for (PresetDto preset : pack.presets()) {
      if (preset.squadMin() > preset.squadMax()) {
        out.add(
            violation(
                file,
                pack,
                "squad-bounds",
                "preset '%s' has squad min %d above squad max %d"
                    .formatted(preset.id(), preset.squadMin(), preset.squadMax())));
      }
      preset.roleMinimums().entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .filter(e -> e.getValue() < 0)
          .forEach(
              e ->
                  out.add(
                      violation(
                          file,
                          pack,
                          "negative-role-minimum",
                          "preset '%s' has a negative minimum (%d) for role '%s'"
                              .formatted(preset.id(), e.getValue(), e.getKey()))));
      var ladder = preset.incrementLadder();
      for (int i = 1; i < ladder.size(); i++) {
        if (ladder.get(i).from() <= ladder.get(i - 1).from()) {
          out.add(
              violation(
                  file,
                  pack,
                  "ladder-order",
                  "preset '%s' increment ladder is not strictly ascending: %d follows %d"
                      .formatted(preset.id(), ladder.get(i).from(), ladder.get(i - 1).from())));
        }
      }
    }

    var tagIds = pack.tags().stream().map(TagDto::id).collect(Collectors.toSet());
    for (RestrictedCategoryDto category : pack.restrictedCategories()) {
      if (!tagIds.contains(category.tag())) {
        out.add(
            violation(
                file,
                pack,
                "category-unknown-tag",
                "restricted category '%s' names tag '%s', which is not in the tag list"
                    .formatted(category.id(), category.tag())));
      }
      for (PresetDto preset : pack.presets()) {
        if (category.max() > preset.squadMax()) {
          out.add(
              violation(
                  file,
                  pack,
                  "category-max",
                  "restricted category '%s' max %d is above squad max %d of preset '%s'"
                      .formatted(category.id(), category.max(), preset.squadMax(), preset.id())));
        }
      }
    }

    var appearance = pack.appearanceRule();
    if (appearance != null
        && pack.statSchema().stream().noneMatch(st -> st.id().equals(appearance.stat()))) {
      out.add(
          violation(
              file,
              pack,
              "appearance-unknown-stat",
              "appearance rule names stat '%s', which is not in the stat schema"
                  .formatted(appearance.stat())));
    }
  }

  private void uniqueIds(
      PackDto pack, String file, String list, List<String> ids, List<PackViolation> out) {
    var seen = new java.util.HashSet<String>();
    var reported = new java.util.HashSet<String>();
    for (String id : ids) {
      if (!seen.add(id) && reported.add(id)) {
        out.add(
            violation(
                file,
                pack,
                "duplicate-id",
                "id '%s' appears more than once in %s".formatted(id, list)));
      }
    }
  }

  /** FR-143: roles, role minimums and rule stats must all exist in the pack. */
  private void checkReferences(PackDto pack, String file, List<PackViolation> out) {
    var roleIds = pack.roles().stream().map(RoleDto::id).collect(Collectors.toSet());
    var stats =
        pack.statSchema().stream()
            .collect(Collectors.toMap(StatDto::id, Function.identity(), (a, b) -> a));

    for (RuleDto rule : flatten(pack.defaultRuleSet().rules())) {
      switch (rule) {
        case RuleDto.RoleConditional r ->
            r.roles().stream()
                .filter(role -> !roleIds.contains(role))
                .forEach(
                    role ->
                        out.add(
                            violation(
                                file,
                                pack,
                                "unknown-role",
                                "rule '%s' names role '%s', which is not in the role list"
                                    .formatted(r.id(), role))));
        case RuleDto.PerUnit r -> requireStat(pack, file, stats, r.id(), r.stat(), out);
        case RuleDto.ThresholdBonus r -> requireStat(pack, file, stats, r.id(), r.stat(), out);
        case RuleDto.BandedRate r -> {
          requireStat(pack, file, stats, r.id(), r.numerator(), out);
          requireStat(pack, file, stats, r.id(), r.denominator(), out);
          requireStat(pack, file, stats, r.id(), r.minVolume().stat(), out);
        }
        case RuleDto.EventTypeBonus r -> {
          if (requireStat(pack, file, stats, r.id(), r.stat(), out)
              && !stats.get(r.stat()).eventTypes().contains(r.eventType())) {
            out.add(
                violation(
                    file,
                    pack,
                    "unknown-event-type",
                    "rule '%s' names event type '%s', which stat '%s' does not declare"
                        .formatted(r.id(), r.eventType(), r.stat())));
          }
        }
        case RuleDto.AppearanceBonus r -> {}
      }
    }

    for (PresetDto preset : pack.presets()) {
      preset.roleMinimums().keySet().stream()
          .sorted()
          .filter(role -> !roleIds.contains(role))
          .forEach(
              role ->
                  out.add(
                      violation(
                          file,
                          pack,
                          "unknown-role",
                          "preset '%s' has a role minimum for role '%s', which is not in the role list"
                              .formatted(preset.id(), role))));
      int sum = preset.roleMinimums().values().stream().mapToInt(Integer::intValue).sum();
      if (sum > preset.squadMin()) {
        out.add(
            violation(
                file,
                pack,
                "role-minimums-exceed-squad-min",
                "preset '%s' role minimums sum to %d, more than squad min %d"
                    .formatted(preset.id(), sum, preset.squadMin())));
      }
    }
  }

  private boolean requireStat(
      PackDto pack,
      String file,
      Map<String, StatDto> stats,
      String ruleId,
      String stat,
      List<PackViolation> out) {
    if (stats.containsKey(stat)) {
      return true;
    }
    out.add(
        violation(
            file,
            pack,
            "unknown-stat",
            "rule '%s' names stat '%s', which is not in the stat schema".formatted(ruleId, stat)));
    return false;
  }

  /** The rules in order, with the rule nested in each role-conditional following its parent. */
  private static List<RuleDto> flatten(List<RuleDto> rules) {
    var all = new ArrayList<RuleDto>();
    for (RuleDto rule : rules) {
      all.add(rule);
      if (rule instanceof RuleDto.RoleConditional conditional) {
        all.addAll(flatten(List.of(conditional.rule())));
      }
    }
    return all;
  }

  private static PackViolation violation(String file, PackDto pack, String check, String message) {
    return new PackViolation(file, pack.id(), check, message);
  }
}
