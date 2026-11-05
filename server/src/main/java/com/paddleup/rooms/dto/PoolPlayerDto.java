package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** One player of a room's pool. {@code role} and {@code tags} are keys of the room's pack. */
public record PoolPlayerDto(
    @Schema(requiredMode = REQUIRED) UUID id,
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(requiredMode = REQUIRED) String country,
    @Schema(requiredMode = REQUIRED) String role,
    @Schema(requiredMode = REQUIRED) List<String> tags,
    @Schema(requiredMode = REQUIRED) long basePrice,
    @Schema(requiredMode = REQUIRED) String set,
    @Schema(nullable = true) String providerId,
    @Schema(requiredMode = REQUIRED) PoolOrigin origin) {}
