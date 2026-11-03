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

  /**
   * Applies every check to a parsed pack. A section named in {@code partial} lost entries while
   * parsing, so a check that looks something up in it is skipped; the parse already reported the
   * gap.
   */
  List<PackViolation> validate(PackDto pack, String file, Set<String> partial) {
    var violations = new ArrayList<PackViolation>();
    checkReferences(pack, file, partial, violations);
    checkStructure(pack, file, partial, violations);
    return violations;
  }

  /** Structural checks: unique ids, sane bounds, ordered ladders, valid tag and stat references. */
  private void checkStructure(
      PackDto pack, String file, Set<String> partial, List<PackViolation> out) {
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
      if (preset.squadMin() < 0) {
        out.add(
            violation(
                file,
                pack,
                "negative-squad-min",
                "preset '%s' has a negative squad min (%d)"
                    .formatted(preset.id(), preset.squadMin())));
      }
      if (preset.displayUnit().divisor() <= 0) {
        out.add(
            violation(
                file,
                pack,
                "display-divisor",
                "preset '%s' has display divisor %d; it must be above zero"
                    .formatted(preset.id(), preset.displayUnit().divisor())));
      }
      for (var step : preset.incrementLadder()) {
        if (step.step() <= 0) {
          out.add(
              violation(
                  file,
                  pack,
                  "ladder-step",
                  "preset '%s' increment ladder has step %d from %d; it must be above zero"
                      .formatted(preset.id(), step.step(), step.from())));
        }
      }
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
    for (PresetDto preset : pack.presets()) {
      checkRetentionTags(pack, file, partial, tagIds, preset, out);
      if (preset.poolSize() != null && preset.poolSize() < preset.squadMax()) {
        out.add(
            violation(
                file,
                pack,
                "pool-size",
                "preset '%s' has pool size %d below squad max %d"
                    .formatted(preset.id(), preset.poolSize(), preset.squadMax())));
      }
    }
    for (RestrictedCategoryDto category : pack.restrictedCategories()) {
      if (category.max() < 0) {
        out.add(
            violation(
                file,
                pack,
                "negative-category-max",
                "restricted category '%s' has a negative max (%d)"
                    .formatted(category.id(), category.max())));
      }
      if (!partial.contains("tags") && !tagIds.contains(category.tag())) {
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
        && !partial.contains("statSchema")
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

  /** Every tag a preset's retention limits or slabs name must be in the tag list. */
  private void checkRetentionTags(
      PackDto pack,
      String file,
      Set<String> partial,
      Set<String> tagIds,
      PresetDto preset,
      List<PackViolation> out) {
    if (partial.contains("tags") || preset.retention() == null) {
      return;
    }
    var named = new java.util.TreeSet<String>(preset.retention().tagLimits().keySet());
    preset.retention().slabs().forEach(slab -> named.add(slab.tag()));
    named.stream()
        .filter(tag -> !tagIds.contains(tag))
        .forEach(
            tag ->
                out.add(
                    violation(
                        file,
                        pack,
                        "retention-unknown-tag",
                        "preset '%s' retention names tag '%s', which is not in the tag list"
                            .formatted(preset.id(), tag))));
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
  private void checkReferences(
      PackDto pack, String file, Set<String> partial, List<PackViolation> out) {
    var rolesPartial = partial.contains("roles");
    var statsPartial = partial.contains("statSchema");
    var roleIds = pack.roles().stream().map(RoleDto::id).collect(Collectors.toSet());
    var stats =
        pack.statSchema().stream()
            .collect(Collectors.toMap(StatDto::id, Function.identity(), (a, b) -> a));

    for (RuleDto rule : flatten(pack.defaultRuleSet().rules())) {
      switch (rule) {
        case RuleDto.RoleConditional r ->
            r.roles().stream()
                .filter(role -> !rolesPartial && !roleIds.contains(role))
                .forEach(
                    role ->
                        out.add(
                            violation(
                                file,
                                pack,
                                "unknown-role",
                                "rule '%s' names role '%s', which is not in the role list"
                                    .formatted(r.id(), role))));
        case RuleDto.PerUnit r ->
            requireStat(pack, file, stats, statsPartial, r.id(), r.stat(), out);
        case RuleDto.ThresholdBonus r ->
            requireStat(pack, file, stats, statsPartial, r.id(), r.stat(), out);
        case RuleDto.BandedRate r -> {
          requireStat(pack, file, stats, statsPartial, r.id(), r.numerator(), out);
          requireStat(pack, file, stats, statsPartial, r.id(), r.denominator(), out);
          requireStat(pack, file, stats, statsPartial, r.id(), r.minVolume().stat(), out);
        }
        case RuleDto.EventTypeBonus r -> {
          if (requireStat(pack, file, stats, statsPartial, r.id(), r.stat(), out)
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
          .filter(role -> !rolesPartial && !roleIds.contains(role))
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
      boolean statsPartial,
      String ruleId,
      String stat,
      List<PackViolation> out) {
    if (stats.containsKey(stat)) {
      return true;
    }
    if (statsPartial) {
      return false;
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
