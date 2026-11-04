package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.paddleup.packs.dto.RestrictedCategoryDto;
import com.paddleup.packs.dto.RoleDto;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.packs.dto.TagDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pack vocabulary copied into a room when it is created. A room reads labels and valid ids from
 * this snapshot and never from the pack again.
 */
public record PackLabelsDto(
    @Schema(requiredMode = REQUIRED) String packId,
    @Schema(requiredMode = REQUIRED) String packVersion,
    @Schema(requiredMode = REQUIRED) List<RoleDto> roles,
    @Schema(requiredMode = REQUIRED) List<TagDto> tags,
    @Schema(requiredMode = REQUIRED) List<RestrictedCategoryDto> restrictedCategories,
    @Schema(requiredMode = REQUIRED) List<StatDto> stats,
    @Schema(requiredMode = REQUIRED) Map<String, String> terminology,
    @Schema(nullable = true) String packName) {

  public PackLabelsDto {
    roles = roles == null ? List.of() : List.copyOf(roles);
    tags = tags == null ? List.of() : List.copyOf(tags);
    restrictedCategories =
        restrictedCategories == null ? List.of() : List.copyOf(restrictedCategories);
    stats = stats == null ? List.of() : List.copyOf(stats);
    terminology = terminology == null ? Map.of() : new LinkedHashMap<>(terminology);
  }

  /** The constructor from before the pack display name was snapshotted. */
  public PackLabelsDto(
      String packId,
      String packVersion,
      List<RoleDto> roles,
      List<TagDto> tags,
      List<RestrictedCategoryDto> restrictedCategories,
      List<StatDto> stats,
      Map<String, String> terminology) {
    this(packId, packVersion, roles, tags, restrictedCategories, stats, terminology, null);
  }
}
