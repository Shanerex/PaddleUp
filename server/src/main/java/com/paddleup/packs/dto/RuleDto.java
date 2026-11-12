package com.paddleup.packs.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * One scoring rule. The JSON {@code type} names one of the six primitives; evaluating a rule is the
 * scoring module's job, this type only carries its parameters. Points are exact decimals.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = RuleDto.PerUnit.class, name = RuleDto.PER_UNIT),
  @JsonSubTypes.Type(value = RuleDto.ThresholdBonus.class, name = RuleDto.THRESHOLD_BONUS),
  @JsonSubTypes.Type(value = RuleDto.BandedRate.class, name = RuleDto.BANDED_RATE),
  @JsonSubTypes.Type(value = RuleDto.RoleConditional.class, name = RuleDto.ROLE_CONDITIONAL),
  @JsonSubTypes.Type(value = RuleDto.EventTypeBonus.class, name = RuleDto.EVENT_TYPE_BONUS),
  @JsonSubTypes.Type(value = RuleDto.AppearanceBonus.class, name = RuleDto.APPEARANCE_BONUS)
})
public sealed interface RuleDto {

  String PER_UNIT = "per-unit";
  String THRESHOLD_BONUS = "threshold-bonus";
  String BANDED_RATE = "banded-rate";
  String ROLE_CONDITIONAL = "role-conditional";
  String EVENT_TYPE_BONUS = "event-type-bonus";
  String APPEARANCE_BONUS = "appearance-bonus";

  /** The accepted values of the {@code type} property. */
  List<String> TYPES =
      List.of(
          PER_UNIT,
          THRESHOLD_BONUS,
          BANDED_RATE,
          ROLE_CONDITIONAL,
          EVENT_TYPE_BONUS,
          APPEARANCE_BONUS);

  String id();

  /** The name shown for this rule on a breakdown line; null falls back to the stat label or id. */
  String label();

  /** Points for each unit of a stat. */
  record PerUnit(String id, String stat, BigDecimal points, String label) implements RuleDto {
    public PerUnit(String id, String stat, BigDecimal points) {
      this(id, stat, points, null);
    }
  }

  /** A bonus for reaching a threshold of a stat; every tier reached adds its points. */
  record ThresholdBonus(String id, String stat, List<Tier> tiers, String label) implements RuleDto {
    public ThresholdBonus {
      tiers = tiers == null ? List.of() : List.copyOf(tiers);
    }

    public ThresholdBonus(String id, String stat, List<Tier> tiers) {
      this(id, stat, tiers, null);
    }
  }

  record Tier(BigDecimal atLeast, BigDecimal points) {}

  /**
   * Points by band of a rate (numerator divided by denominator, times scale), given a minimum
   * volume.
   */
  record BandedRate(
      String id,
      String numerator,
      String denominator,
      BigDecimal scale,
      MinVolume minVolume,
      List<Band> bands,
      String label)
      implements RuleDto {
    public BandedRate {
      bands = bands == null ? List.of() : List.copyOf(bands);
    }

    public BandedRate(
        String id,
        String numerator,
        String denominator,
        BigDecimal scale,
        MinVolume minVolume,
        List<Band> bands) {
      this(id, numerator, denominator, scale, minVolume, bands, null);
    }

    /**
     * The rate to look up in the bands: numerator times scale, divided by the denominator, to 2 dp.
     * Empty when the denominator is not above zero (for example a manual entry that left the
     * denominator stat at 0), which means no band points. {@code minVolume} does not guard this, as
     * it reads a different stat.
     */
    public Optional<BigDecimal> rate(BigDecimal numerator, BigDecimal denominator) {
      if (numerator == null || denominator == null || denominator.signum() <= 0) {
        return Optional.empty();
      }
      return Optional.of(
          numerator.multiply(scale).divide(denominator, 2, java.math.RoundingMode.HALF_UP));
    }
  }

  record MinVolume(String stat, BigDecimal atLeast) {}

  record Band(BigDecimal from, BigDecimal to, BigDecimal points) {}

  /** Applies a nested rule only to players holding one of the listed roles. */
  record RoleConditional(String id, List<String> roles, RuleDto rule, String label)
      implements RuleDto {
    public RoleConditional {
      roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public RoleConditional(String id, List<String> roles, RuleDto rule) {
      this(id, roles, rule, null);
    }
  }

  /** Points for each occurrence of one event type recorded on a stat. */
  record EventTypeBonus(String id, String stat, String eventType, BigDecimal points, String label)
      implements RuleDto {
    public EventTypeBonus(String id, String stat, String eventType, BigDecimal points) {
      this(id, stat, eventType, points, null);
    }
  }

  /** Flat points for appearing, as defined by the pack's appearance rule. */
  record AppearanceBonus(String id, BigDecimal points, String label) implements RuleDto {
    public AppearanceBonus(String id, BigDecimal points) {
      this(id, points, null);
    }
  }
}
