package com.paddleup.rooms.service;

import com.paddleup.accounts.service.DisplayNameSanitiser;
import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.dto.RoleDto;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.packs.dto.TagDto;
import com.paddleup.rooms.dto.AiSettingsDto;
import com.paddleup.rooms.dto.PackLabelsDto;
import com.paddleup.rooms.dto.RoomConfigDto;
import com.paddleup.rooms.dto.Violation;
import com.paddleup.rooms.model.FranchiseColour;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Checks a room config against the room's pack snapshot and returns every broken rule, never just
 * the first. Role, tag, category and stat ids must exist in the snapshot, not in the live pack.
 */
@Component
class RoomConfigValidator {

  static final int NAME_MIN = 2;
  static final int NAME_MAX = 40;
  static final int COMPETITION_MAX = 60;
  static final int MANAGERS_MIN = 2;
  static final int CURRENCY_MAX = 10;
  static final int LABEL_MAX = 20;
  static final int RULE_ID_MAX = 60;

  /** Largest list or map a config may carry, so a saved config stays small. */
  static final int COLLECTION_MAX = 50;

  static final int SCORING_RULES_MAX = 100;

  /** Most role-conditional rules that may wrap one another. */
  static final int RULE_NESTING_MAX = 3;

  /** Largest magnitude of a points, threshold, scale or band value in a scoring rule. */
  static final BigDecimal RULE_NUMBER_MAX = BigDecimal.valueOf(1_000_000);

  /** Most decimal places such a value may carry. */
  static final int RULE_NUMBER_DECIMALS_MAX = 4;

  /**
   * Lowest scale such a value may have. A value up to 1,000,000 needs no more than 6 trailing
   * powers of ten, and a larger exponent (even on zero) is something Postgres refuses.
   */
  static final int RULE_NUMBER_SCALE_MIN = -6;

  /** Longest id echoed back in a violation. */
  static final int ECHO_MAX = 40;

  /** Every violation of {@code config}; empty when it is valid. */
  public List<Violation> validate(
      RoomConfigDto config, PackLabelsDto snapshot, int franchiseCount) {
    var out = new ArrayList<Violation>();
    names(config, out);
    managers(config, franchiseCount, out);
    money(config, out);
    ladder(config, out);
    squad(config, snapshot, out);
    quotas(config, snapshot, out);
    retention(config, snapshot, out);
    ai(config.ai(), out);
    scoring(config, snapshot, out);
    return out;
  }

  private static void names(RoomConfigDto c, List<Violation> out) {
    int name = c.name() == null ? 0 : c.name().codePointCount(0, c.name().length());
    if (name < NAME_MIN || name > NAME_MAX) {
      out.add(
          new Violation("name", "Name must be " + NAME_MIN + " to " + NAME_MAX + " characters"));
    }
    if (c.competition() != null
        && c.competition().codePointCount(0, c.competition().length()) > COMPETITION_MAX) {
      out.add(new Violation("competition", "Must be at most " + COMPETITION_MAX + " characters"));
    }
  }

  private static void managers(RoomConfigDto c, int franchiseCount, List<Violation> out) {
    int ceiling = FranchiseColour.values().length;
    if (c.minManagers() < MANAGERS_MIN) {
      out.add(new Violation("minManagers", "Must be at least " + MANAGERS_MIN));
    }
    if (c.maxManagers() < c.minManagers()) {
      out.add(new Violation("maxManagers", "Must be at least minManagers"));
    }
    if (c.maxManagers() > ceiling) {
      out.add(new Violation("maxManagers", "Must be at most " + ceiling + " (one colour each)"));
    }
    if (c.maxManagers() < franchiseCount) {
      out.add(
          new Violation(
              "maxManagers",
              "Must be at least " + franchiseCount + ", the franchises already in the room"));
    }
  }

  private static void money(RoomConfigDto c, List<Violation> out) {
    if (c.purse() <= 0) {
      out.add(new Violation("purse", "Must be greater than 0"));
    }
    if (c.currencyCode() == null || c.currencyCode().isBlank()) {
      out.add(new Violation("currencyCode", "Is required"));
    } else if (!cleanText(c.currencyCode(), CURRENCY_MAX)) {
      out.add(
          new Violation(
              "currencyCode", "Must be plain text of at most " + CURRENCY_MAX + " characters"));
    }
    PresetDto.DisplayUnit unit = c.displayUnit();
    if (unit == null || unit.label() == null || unit.label().isBlank() || unit.divisor() <= 0) {
      out.add(new Violation("displayUnit", "Needs a label and a divisor above 0"));
    } else if (!cleanText(unit.label(), LABEL_MAX)) {
      out.add(
          new Violation(
              "displayUnit", "Label must be plain text of at most " + LABEL_MAX + " characters"));
    }
  }

  /** True when {@code text} survives sanitising unchanged and is within {@code max} characters. */
  private static boolean cleanText(String text, int max) {
    return text.codePointCount(0, text.length()) <= max
        && text.equals(DisplayNameSanitiser.sanitise(text));
  }

  /** {@code text} cut to {@link #ECHO_MAX} characters, so a hostile id is not echoed whole. */
  private static String clip(String text) {
    if (text == null) {
      return "";
    }
    return text.codePointCount(0, text.length()) <= ECHO_MAX
        ? text
        : text.substring(0, text.offsetByCodePoints(0, ECHO_MAX)) + "...";
  }

  /** Adds a violation and returns true when {@code size} is above {@code max}. */
  private static boolean tooMany(int size, int max, String field, List<Violation> out) {
    if (size > max) {
      out.add(new Violation(field, "Must have at most " + max + " entries"));
      return true;
    }
    return false;
  }

  private static void ladder(RoomConfigDto c, List<Violation> out) {
    if (c.incrementLadder().isEmpty()) {
      out.add(new Violation("incrementLadder", "Needs at least one step"));
      return;
    }
    if (tooMany(c.incrementLadder().size(), COLLECTION_MAX, "incrementLadder", out)) {
      return;
    }
    long previous = -1;
    for (int i = 0; i < c.incrementLadder().size(); i++) {
      PresetDto.IncrementStep step = c.incrementLadder().get(i);
      String at = "incrementLadder[" + i + "]";
      if (step == null) {
        out.add(new Violation(at, "Is required"));
        continue;
      }
      if (step.step() <= 0) {
        out.add(new Violation(at + ".step", "Must be greater than 0"));
      }
      if (step.from() < 0 || step.from() <= previous) {
        out.add(new Violation(at + ".from", "Must be 0 or more and above the previous step"));
      }
      previous = Math.max(previous, step.from());
    }
  }

  private static void squad(RoomConfigDto c, PackLabelsDto snapshot, List<Violation> out) {
    if (c.squadMin() < 1) {
      out.add(new Violation("squadMin", "Must be at least 1"));
    }
    if (c.squadMin() > c.squadMax()) {
      out.add(new Violation("squadMin", "Must not exceed squadMax"));
    }
    Set<String> roles = snapshot.roles().stream().map(RoleDto::id).collect(Collectors.toSet());
    long sum = 0;
    if (tooMany(c.roleMinimums().size(), COLLECTION_MAX, "roleMinimums", out)) {
      return;
    }
    for (Map.Entry<String, Integer> entry : c.roleMinimums().entrySet()) {
      String at = "roleMinimums." + clip(entry.getKey());
      if (!roles.contains(entry.getKey())) {
        out.add(new Violation(at, "Is not a role of this pack"));
      }
      if (entry.getValue() == null || entry.getValue() < 0) {
        out.add(new Violation(at, "Must be a whole number, 0 or more"));
      } else {
        sum += entry.getValue();
      }
    }
    if (sum > c.squadMin()) {
      out.add(new Violation("roleMinimums", "Minimums add up to " + sum + ", above squadMin"));
    }
  }

  private static void quotas(RoomConfigDto c, PackLabelsDto snapshot, List<Violation> out) {
    Set<String> categories =
        snapshot.restrictedCategories().stream().map(rc -> rc.id()).collect(Collectors.toSet());
    if (tooMany(c.categoryQuotas().size(), COLLECTION_MAX, "categoryQuotas", out)) {
      return;
    }
    for (Map.Entry<String, Integer> entry : c.categoryQuotas().entrySet()) {
      String at = "categoryQuotas." + clip(entry.getKey());
      if (!categories.contains(entry.getKey())) {
        out.add(new Violation(at, "Is not a restricted category of this pack"));
      }
      if (entry.getValue() == null || entry.getValue() < 0) {
        out.add(new Violation(at, "Must be a whole number, 0 or more"));
      }
    }
  }

  private static void retention(RoomConfigDto c, PackLabelsDto snapshot, List<Violation> out) {
    PresetDto.Retention r = c.retention();
    if (r == null) {
      return;
    }
    if (r.maxRetentions() < 0) {
      out.add(new Violation("retention.maxRetentions", "Must be 0 or more"));
    }
    if (r.rtmCards() < 0) {
      out.add(new Violation("retention.rtmCards", "Must be 0 or more"));
    }
    Set<String> tags = snapshot.tags().stream().map(TagDto::id).collect(Collectors.toSet());
    if (tooMany(r.tagLimits().size(), COLLECTION_MAX, "retention.tagLimits", out)
        || tooMany(r.slabs().size(), COLLECTION_MAX, "retention.slabs", out)) {
      return;
    }
    for (Map.Entry<String, Integer> entry : r.tagLimits().entrySet()) {
      String at = "retention.tagLimits." + clip(entry.getKey());
      if (!tags.contains(entry.getKey())) {
        out.add(new Violation(at, "Is not a tag of this pack"));
      }
      if (entry.getValue() == null || entry.getValue() < 0) {
        out.add(new Violation(at, "Must be a whole number, 0 or more"));
      }
    }
    for (int i = 0; i < r.slabs().size(); i++) {
      PresetDto.Slab slab = r.slabs().get(i);
      String at = "retention.slabs[" + i + "]";
      if (slab == null || !tags.contains(slab.tag())) {
        out.add(new Violation(at + ".tag", "Is not a tag of this pack"));
      } else if (tooMany(slab.prices().size(), COLLECTION_MAX, at + ".prices", out)) {
        continue;
      } else if (slab.prices().stream().anyMatch(p -> p == null || p < 0)) {
        out.add(new Violation(at + ".prices", "Must be 0 or more"));
      }
    }
  }

  private static void ai(AiSettingsDto ai, List<Violation> out) {
    if (ai == null) {
      out.add(new Violation("ai", "Is required"));
      return;
    }
    if (tooMany(ai.lifelines().size(), COLLECTION_MAX, "ai.lifelines", out)) {
      return;
    }
    for (Map.Entry<String, Integer> entry : ai.lifelines().entrySet()) {
      String at = "ai.lifelines." + clip(entry.getKey());
      if (!RoomConfigFactory.LIFELINE_TYPES.contains(entry.getKey())) {
        out.add(new Violation(at, "Is not a lifeline type"));
      }
      if (entry.getValue() == null || entry.getValue() < 0) {
        out.add(new Violation(at, "Must be a whole number, 0 or more"));
      }
    }
    if (ai.reportCardVisibility() == null
        || !RoomConfigFactory.REPORT_VISIBILITIES.contains(ai.reportCardVisibility())) {
      out.add(
          new Violation(
              "ai.reportCardVisibility",
              "Must be one of " + String.join(", ", RoomConfigFactory.REPORT_VISIBILITIES)));
    }
  }

  private static void scoring(RoomConfigDto c, PackLabelsDto snapshot, List<Violation> out) {
    var checker = new RuleChecker(snapshot, out);
    List<RuleDto> rules = c.scoringRules().rules();
    if (tooMany(rules.size(), SCORING_RULES_MAX, "scoringRules.rules", out)) {
      return;
    }
    for (int i = 0; i < rules.size(); i++) {
      checker.check(rules.get(i), "scoringRules.rules[" + i + "]", 0);
    }
  }

  /** Walks scoring rules, including nested ones, checking each id against the snapshot. */
  private static final class RuleChecker {
    private final Set<String> roles;
    private final Map<String, Set<String>> statEvents = new HashMap<>();
    private final List<Violation> out;

    RuleChecker(PackLabelsDto snapshot, List<Violation> out) {
      this.out = out;
      this.roles = snapshot.roles().stream().map(RoleDto::id).collect(Collectors.toSet());
      for (StatDto stat : snapshot.stats()) {
        statEvents.put(stat.id(), new HashSet<>(stat.eventTypes()));
      }
    }

    /** Checks {@code rule}; {@code wrappers} is how many role-conditional rules enclose it. */
    void check(RuleDto rule, String at, int wrappers) {
      if (rule != null) {
        String id = rule.id();
        if (id == null || id.isBlank() || !cleanText(id, RULE_ID_MAX)) {
          out.add(
              new Violation(
                  at + ".id", "Must be plain text of 1 to " + RULE_ID_MAX + " characters"));
        }
      }
      switch (rule) {
        case null -> out.add(new Violation(at, "Is required"));
        case RuleDto.PerUnit r -> {
          stat(r.stat(), at + ".stat");
          number(r.points(), at + ".points");
        }
        case RuleDto.ThresholdBonus r -> {
          stat(r.stat(), at + ".stat");
          if (!tooMany(r.tiers().size(), COLLECTION_MAX, at + ".tiers", out)) {
            for (int i = 0; i < r.tiers().size(); i++) {
              RuleDto.Tier tier = r.tiers().get(i);
              if (tier != null) {
                number(tier.atLeast(), at + ".tiers[" + i + "].atLeast");
                number(tier.points(), at + ".tiers[" + i + "].points");
              }
            }
          }
        }
        case RuleDto.BandedRate r -> {
          stat(r.numerator(), at + ".numerator");
          stat(r.denominator(), at + ".denominator");
          number(r.scale(), at + ".scale");
          if (r.minVolume() != null) {
            stat(r.minVolume().stat(), at + ".minVolume.stat");
            number(r.minVolume().atLeast(), at + ".minVolume.atLeast");
          }
          if (!tooMany(r.bands().size(), COLLECTION_MAX, at + ".bands", out)) {
            for (int i = 0; i < r.bands().size(); i++) {
              RuleDto.Band band = r.bands().get(i);
              if (band != null) {
                number(band.from(), at + ".bands[" + i + "].from");
                number(band.to(), at + ".bands[" + i + "].to");
                number(band.points(), at + ".bands[" + i + "].points");
              }
            }
          }
        }
        case RuleDto.RoleConditional r -> {
          if (wrappers >= RULE_NESTING_MAX) {
            out.add(
                new Violation(
                    at, "Role-conditional rules may nest at most " + RULE_NESTING_MAX + " deep"));
            return;
          }
          if (!tooMany(r.roles().size(), COLLECTION_MAX, at + ".roles", out)) {
            for (String role : r.roles()) {
              if (!roles.contains(role)) {
                out.add(
                    new Violation(
                        at + ".roles", "'" + clip(role) + "' is not a role of this pack"));
              }
            }
          }
          check(r.rule(), at + ".rule", wrappers + 1);
        }
        case RuleDto.EventTypeBonus r -> {
          stat(r.stat(), at + ".stat");
          Set<String> types = statEvents.get(r.stat());
          if (types != null && !types.contains(r.eventType())) {
            out.add(
                new Violation(
                    at + ".eventType",
                    "'" + clip(r.eventType()) + "' is not an event type of the stat"));
          }
          number(r.points(), at + ".points");
        }
        case RuleDto.AppearanceBonus r -> number(r.points(), at + ".points");
      }
    }

    /**
     * A missing value is left to the rule's own handling. The comparison never expands the number,
     * so a huge exponent such as 1e999999999 costs nothing to refuse.
     */
    private void number(BigDecimal value, String at) {
      if (value != null
          && (value.abs().compareTo(RULE_NUMBER_MAX) > 0
              || value.scale() > RULE_NUMBER_DECIMALS_MAX
              || value.scale() < RULE_NUMBER_SCALE_MIN)) {
        out.add(
            new Violation(
                at,
                "Must be between -"
                    + RULE_NUMBER_MAX.toPlainString()
                    + " and "
                    + RULE_NUMBER_MAX.toPlainString()
                    + " with at most "
                    + RULE_NUMBER_DECIMALS_MAX
                    + " decimal places"));
      }
    }

    private void stat(String id, String at) {
      if (!statEvents.containsKey(id)) {
        out.add(new Violation(at, "'" + clip(id) + "' is not a stat of this pack"));
      }
    }
  }
}
