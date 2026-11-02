package com.paddleup.packs.dto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One sport pack with every section of the pack contract. Immutable. */
public record PackDto(
    int schemaVersion,
    String id,
    String version,
    List<RoleDto> roles,
    List<TagDto> tags,
    List<RestrictedCategoryDto> restrictedCategories,
    List<PresetDto> presets,
    List<StatDto> statSchema,
    RuleSetDto defaultRuleSet,
    AppearanceRuleDto appearanceRule,
    Optional<String> adapterKey,
    Map<String, String> terminology,
    String aiDomainContext) {

  public PackDto {
    roles = roles == null ? List.of() : List.copyOf(roles);
    tags = tags == null ? List.of() : List.copyOf(tags);
    restrictedCategories =
        restrictedCategories == null ? List.of() : List.copyOf(restrictedCategories);
    presets = presets == null ? List.of() : List.copyOf(presets);
    statSchema = statSchema == null ? List.of() : List.copyOf(statSchema);
    adapterKey = adapterKey == null ? Optional.empty() : adapterKey;
    terminology = terminology == null ? Map.of() : Map.copyOf(terminology);
  }

  /** True when the pack names no data adapter, so stats arrive only by manual entry. */
  public boolean manualEntryOnly() {
    return adapterKey.isEmpty();
  }
}
