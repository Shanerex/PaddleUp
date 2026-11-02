package com.paddleup.packs.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
import java.util.List;

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

  /** Points for each unit of a stat. */
  record PerUnit(String id, String stat, BigDecimal points) implements RuleDto {}

  /** A bonus for reaching a threshold of a stat; the highest tier reached applies. */
  record ThresholdBonus(String id, String stat, List<Tier> tiers) implements RuleDto {
    public ThresholdBonus {
      tiers = tiers == null ? List.of() : List.copyOf(tiers);
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
      List<Band> bands)
      implements RuleDto {
    public BandedRate {
      bands = bands == null ? List.of() : List.copyOf(bands);
    }
  }

  record MinVolume(String stat, BigDecimal atLeast) {}

  record Band(BigDecimal from, BigDecimal to, BigDecimal points) {}

  /** Applies a nested rule only to players holding one of the listed roles. */
  record RoleConditional(String id, List<String> roles, RuleDto rule) implements RuleDto {
    public RoleConditional {
      roles = roles == null ? List.of() : List.copyOf(roles);
    }
  }

  /** Points for each occurrence of one event type recorded on a stat. */
  record EventTypeBonus(String id, String stat, String eventType, BigDecimal points)
      implements RuleDto {}

  /** Flat points for appearing, as defined by the pack's appearance rule. */
  record AppearanceBonus(String id, BigDecimal points) implements RuleDto {}
}
