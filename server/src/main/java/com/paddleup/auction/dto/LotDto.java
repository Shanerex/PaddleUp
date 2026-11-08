package com.paddleup.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** A lot's player card, frozen at go-live. */
public record LotDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int no,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID playerId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String country,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String role,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> tags,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String set,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int position,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long basePrice) {}
