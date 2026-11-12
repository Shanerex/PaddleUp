package com.paddleup.scoring.service;

import com.paddleup.packs.dto.AppearanceRuleDto;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.scoring.dto.EvaluationDto;
import com.paddleup.scoring.dto.PointsLineDto;
import com.paddleup.scoring.model.StatLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Turns a rule set, an appearance rule, a role and one player's effective stat line into a total
 * plus one line per rule that fired. Pure: no state, no clock, no I/O, so the same inputs always
 * give the same output. It knows the six primitives and nothing about any sport; every stat, role
 * and event type it touches comes from the rules it is given.
 */
public final class PointsEvaluator {

  private PointsEvaluator() {}

  /**
   * Scores one player in one match. A line is emitted only when a rule fires with non-zero points,
   * in rule order. Missing stats count as 0; a rate whose denominator is not above 0 gives no band.
   *
   * @param stats the room's stat schema, used only for the label fallback
   * @param appearance null when the room has none, which switches appearance bonuses off
   */
  public static EvaluationDto evaluate(
      RuleSetDto rules,
      AppearanceRuleDto appearance,
      List<StatDto> stats,
      String role,
      StatLine line) {
    Map<String, String> statLabels =
        stats.stream()
            .filter(s -> s.label() != null && !s.label().isBlank())
            .collect(Collectors.toMap(StatDto::id, StatDto::label, (a, b) -> a));
    var ctx = new Context(appearance, statLabels, role, line == null ? StatLine.EMPTY : line);
    var lines = new ArrayList<PointsLineDto>();
    BigDecimal total = BigDecimal.ZERO;
    for (RuleDto rule : rules.rules()) {
      Optional<BigDecimal> points = apply(rule, ctx);
      if (points.isPresent() && points.get().signum() != 0) {
        BigDecimal p = normalise(points.get());
        lines.add(new PointsLineDto(rule.id(), label(rule, ctx.statLabels), p));
        total = total.add(p);
      }
    }
    return new EvaluationDto(normalise(total), lines);
  }

  private record Context(
      AppearanceRuleDto appearance, Map<String, String> statLabels, String role, StatLine line) {}

  /** The points the rule gives, or empty when it does not apply. */
  private static Optional<BigDecimal> apply(RuleDto rule, Context ctx) {
    return switch (rule) {
      case RuleDto.PerUnit r -> Optional.of(ctx.line.stat(r.stat()).multiply(r.points()));
      case RuleDto.ThresholdBonus r -> Optional.of(threshold(r, ctx.line.stat(r.stat())));
      case RuleDto.BandedRate r -> band(r, ctx.line);
      case RuleDto.RoleConditional r ->
          r.roles().contains(ctx.role) ? apply(r.rule(), ctx) : Optional.empty();
      case RuleDto.EventTypeBonus r ->
          Optional.of(ctx.line.event(r.stat(), r.eventType()).multiply(r.points()));
      case RuleDto.AppearanceBonus r -> appeared(ctx) ? Optional.of(r.points()) : Optional.empty();
    };
  }

  /** Every tier reached adds its points. */
  private static BigDecimal threshold(RuleDto.ThresholdBonus rule, BigDecimal value) {
    BigDecimal sum = BigDecimal.ZERO;
    for (RuleDto.Tier tier : rule.tiers()) {
      if (value.compareTo(tier.atLeast()) >= 0) {
        sum = sum.add(tier.points());
      }
    }
    return sum;
  }

  /**
   * The points of the one band holding the rate, where a band is lower-inclusive and
   * upper-exclusive. Empty when the minimum volume is not met, the numerator stat is absent (an
   * absent stat is not a measured 0), the denominator is not above 0, or no band holds the rate.
   */
  private static Optional<BigDecimal> band(RuleDto.BandedRate rule, StatLine line) {
    if (rule.minVolume() != null
        && line.stat(rule.minVolume().stat()).compareTo(rule.minVolume().atLeast()) < 0) {
      return Optional.empty();
    }
    if (!line.stats().containsKey(rule.numerator())) {
      return Optional.empty();
    }
    return rule.rate(line.stat(rule.numerator()), line.stat(rule.denominator()))
        .flatMap(
            rate ->
                rule.bands().stream()
                    .filter(b -> rate.compareTo(b.from()) >= 0 && rate.compareTo(b.to()) < 0)
                    .map(RuleDto.Band::points)
                    .findFirst());
  }

  private static boolean appeared(Context ctx) {
    AppearanceRuleDto rule = ctx.appearance;
    return rule != null && ctx.line.stat(rule.stat()).compareTo(rule.atLeast()) >= 0;
  }

  /** The rule's own label, else the label of the stat it reads, else its id. */
  private static String label(RuleDto rule, Map<String, String> statLabels) {
    if (rule.label() != null && !rule.label().isBlank()) {
      return rule.label();
    }
    String stat =
        switch (rule) {
          case RuleDto.PerUnit r -> r.stat();
          case RuleDto.ThresholdBonus r -> r.stat();
          case RuleDto.BandedRate r -> r.numerator();
          case RuleDto.EventTypeBonus r -> r.stat();
          case RuleDto.RoleConditional r -> statOf(r.rule());
          case RuleDto.AppearanceBonus r -> null;
        };
    return stat != null && statLabels.containsKey(stat) ? statLabels.get(stat) : rule.id();
  }

  private static String statOf(RuleDto rule) {
    return switch (rule) {
      case RuleDto.PerUnit r -> r.stat();
      case RuleDto.ThresholdBonus r -> r.stat();
      case RuleDto.BandedRate r -> r.numerator();
      case RuleDto.EventTypeBonus r -> r.stat();
      case RuleDto.RoleConditional r -> statOf(r.rule());
      case RuleDto.AppearanceBonus r -> null;
    };
  }

  /** Drops trailing zeros without switching to exponent form, so equal values print the same. */
  static BigDecimal normalise(BigDecimal value) {
    BigDecimal stripped = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }
}
