package com.paddleup.scoring.service;

import com.paddleup.accounts.service.DisplayNameSanitiser;
import com.paddleup.packs.dto.RoleDto;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.rooms.dto.ScoringInputsDto;
import com.paddleup.rooms.dto.Violation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks an edited rule set against the room's own stat and role vocabulary and the caps, and
 * reports every violation, never just the first. A set that passes can be evaluated without a null
 * check or a division by zero.
 */
final class RuleSetValidator {

  static final int RULES_MAX = 100;
  static final int TEXT_MAX = 60;
  static final int COLLECTION_MAX = 50;
  static final int NESTING_MAX = 3;
  static final BigDecimal NUMBER_MAX = BigDecimal.valueOf(1_000_000);
  static final int NUMBER_DECIMALS_MAX = 4;
  static final int NUMBER_SCALE_MIN = -6;

  /** Longest piece of caller text echoed back in a message. */
  private static final int ECHO_MAX = 40;

  private final Set<String> roles;
  private final Map<String, Set<String>> statEvents = new HashMap<>();
  private final Set<String> ids = new HashSet<>();
  private final List<Violation> out = new ArrayList<>();

  private RuleSetValidator(ScoringInputsDto inputs) {
    this.roles = inputs.roles().stream().map(RoleDto::id).collect(Collectors.toSet());
    for (StatDto stat : inputs.stats()) {
      statEvents.put(stat.id(), new HashSet<>(stat.eventTypes()));
    }
  }

  /** Every violation of {@code rules}, in rule order; empty when the set is valid. */
  static List<Violation> validate(RuleSetDto rules, ScoringInputsDto inputs) {
    var validator = new RuleSetValidator(inputs);
    if (rules == null) {
      validator.out.add(new Violation("rules", "Is required"));
      return validator.out;
    }
    if (rules.rules().size() > RULES_MAX) {
      validator.out.add(new Violation("rules", "Must have at most " + RULES_MAX + " rules"));
      return validator.out;
    }
    for (int i = 0; i < rules.rules().size(); i++) {
      validator.rule(rules.rules().get(i), "rules[" + i + "]", 0);
    }
    return validator.out;
  }

  private void rule(RuleDto rule, String at, int wrappers) {
    if (rule == null) {
      out.add(new Violation(at, "Is required"));
      return;
    }
    id(rule.id(), at + ".id");
    label(rule.label(), at + ".label");
    switch (rule) {
      case RuleDto.PerUnit r -> {
        stat(r.stat(), at + ".stat");
        number(r.points(), at + ".points");
      }
      case RuleDto.ThresholdBonus r -> {
        stat(r.stat(), at + ".stat");
        if (!tooMany(r.tiers().size(), at + ".tiers")) {
          for (int i = 0; i < r.tiers().size(); i++) {
            RuleDto.Tier tier = r.tiers().get(i);
            if (tier == null) {
              out.add(new Violation(at + ".tiers[" + i + "]", "Is required"));
              continue;
            }
            number(tier.atLeast(), at + ".tiers[" + i + "].atLeast");
            number(tier.points(), at + ".tiers[" + i + "].points");
          }
        }
      }
      case RuleDto.BandedRate r -> banded(r, at);
      case RuleDto.RoleConditional r -> {
        if (wrappers >= NESTING_MAX) {
          out.add(
              new Violation(
                  at, "Role-conditional rules may nest at most " + NESTING_MAX + " deep"));
          return;
        }
        if (!tooMany(r.roles().size(), at + ".roles")) {
          for (String role : r.roles()) {
            if (!roles.contains(role)) {
              out.add(
                  new Violation(at + ".roles", "'" + clip(role) + "' is not a role of this room"));
            }
          }
        }
        rule(r.rule(), at + ".rule", wrappers + 1);
      }
      case RuleDto.EventTypeBonus r -> {
        stat(r.stat(), at + ".stat");
        Set<String> types = statEvents.get(r.stat());
        if (r.eventType() == null) {
          out.add(new Violation(at + ".eventType", "Is required"));
        } else if (types != null && !types.contains(r.eventType())) {
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

  private void banded(RuleDto.BandedRate r, String at) {
    stat(r.numerator(), at + ".numerator");
    stat(r.denominator(), at + ".denominator");
    number(r.scale(), at + ".scale");
    if (r.scale() != null && r.scale().signum() <= 0) {
      out.add(new Violation(at + ".scale", "Must be above zero"));
    }
    if (r.minVolume() != null) {
      stat(r.minVolume().stat(), at + ".minVolume.stat");
      number(r.minVolume().atLeast(), at + ".minVolume.atLeast");
      if (r.minVolume().atLeast() != null && r.minVolume().atLeast().signum() < 0) {
        out.add(new Violation(at + ".minVolume.atLeast", "Must not be negative"));
      }
    }
    if (tooMany(r.bands().size(), at + ".bands")) {
      return;
    }
    var usable = new ArrayList<Integer>();
    for (int i = 0; i < r.bands().size(); i++) {
      RuleDto.Band band = r.bands().get(i);
      String bandAt = at + ".bands[" + i + "]";
      if (band == null) {
        out.add(new Violation(bandAt, "Is required"));
        continue;
      }
      number(band.from(), bandAt + ".from");
      number(band.to(), bandAt + ".to");
      number(band.points(), bandAt + ".points");
      if (band.from() != null && band.to() != null) {
        if (band.from().compareTo(band.to()) >= 0) {
          out.add(new Violation(bandAt, "'from' must be below 'to'"));
        } else {
          usable.add(i);
        }
      }
    }
    for (int a = 0; a < usable.size(); a++) {
      for (int b = a + 1; b < usable.size(); b++) {
        RuleDto.Band first = r.bands().get(usable.get(a));
        RuleDto.Band second = r.bands().get(usable.get(b));
        if (first.from().compareTo(second.to()) < 0 && second.from().compareTo(first.to()) < 0) {
          out.add(
              new Violation(
                  at + ".bands[" + usable.get(b) + "]",
                  "Overlaps band " + usable.get(a) + "; bands must not overlap"));
        }
      }
    }
  }

  private void id(String id, String at) {
    if (id == null || id.isBlank() || !cleanText(id)) {
      out.add(new Violation(at, "Must be plain text of 1 to " + TEXT_MAX + " characters"));
    } else if (!ids.add(id)) {
      out.add(new Violation(at, "'" + clip(id) + "' is used by another rule"));
    }
  }

  private void label(String label, String at) {
    if (label != null && !cleanText(label)) {
      out.add(new Violation(at, "Must be plain text of at most " + TEXT_MAX + " characters"));
    }
  }

  private static boolean cleanText(String text) {
    return text.codePointCount(0, text.length()) <= TEXT_MAX
        && text.equals(DisplayNameSanitiser.sanitise(text));
  }

  private void stat(String id, String at) {
    if (id == null) {
      out.add(new Violation(at, "Is required"));
    } else if (!statEvents.containsKey(id)) {
      out.add(new Violation(at, "'" + clip(id) + "' is not a stat of this room"));
    }
  }

  /** Required, within the bounds, and never expanded, so a huge exponent costs nothing. */
  private void number(BigDecimal value, String at) {
    if (value == null) {
      out.add(new Violation(at, "Is required"));
    } else if (value.abs().compareTo(NUMBER_MAX) > 0
        || value.scale() > NUMBER_DECIMALS_MAX
        || value.scale() < NUMBER_SCALE_MIN) {
      out.add(
          new Violation(
              at,
              "Must be between -"
                  + NUMBER_MAX.toPlainString()
                  + " and "
                  + NUMBER_MAX.toPlainString()
                  + " with at most "
                  + NUMBER_DECIMALS_MAX
                  + " decimal places"));
    }
  }

  private boolean tooMany(int size, String at) {
    if (size > COLLECTION_MAX) {
      out.add(new Violation(at, "Must have at most " + COLLECTION_MAX + " entries"));
      return true;
    }
    return false;
  }

  private static String clip(String text) {
    if (text == null) {
      return "";
    }
    return text.codePointCount(0, text.length()) <= ECHO_MAX
        ? text
        : text.substring(0, text.offsetByCodePoints(0, ECHO_MAX)) + "...";
  }
}
