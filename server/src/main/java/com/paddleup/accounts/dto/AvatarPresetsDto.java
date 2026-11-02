package com.paddleup.accounts.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Preset avatar ids; the web app maps each id to a bundled image. */
public record AvatarPresetsDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> presets) {}
