package com.paddleup.packs.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** A pack an admin can import season data for, with the keys a file may use. */
public record DatasetPackDto(
    @Schema(requiredMode = REQUIRED) String id,
    @Schema(requiredMode = REQUIRED) String version,
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(nullable = true) String icon,
    @Schema(requiredMode = REQUIRED) List<RoleDto> roles,
    @Schema(requiredMode = REQUIRED) List<TagDto> tags) {}
