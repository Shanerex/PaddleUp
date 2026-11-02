package com.paddleup.accounts.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ProfileDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String avatar,
    @Schema(nullable = true) String googlePictureUrl) {}
