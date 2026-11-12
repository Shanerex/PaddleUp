package com.paddleup.rooms.dto;

import com.paddleup.packs.dto.AppearanceRuleDto;
import com.paddleup.packs.dto.RoleDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.packs.dto.StatDto;
import java.util.List;
import java.util.UUID;

/**
 * What scoring reads of a room: the pack vocabulary and default rules as they were snapshotted when
 * the room was created (never the live pack), the appearance rule, and the season dataset the
 * room's pool was copied from. {@code datasetId} and {@code appearanceRule} may be null.
 */
public record ScoringInputsDto(
    String packId,
    String packVersion,
    List<RoleDto> roles,
    List<StatDto> stats,
    RuleSetDto defaultRules,
    AppearanceRuleDto appearanceRule,
    UUID datasetId) {

  public ScoringInputsDto {
    roles = roles == null ? List.of() : List.copyOf(roles);
    stats = stats == null ? List.of() : List.copyOf(stats);
    defaultRules = defaultRules == null ? new RuleSetDto(List.of()) : defaultRules;
  }
}
