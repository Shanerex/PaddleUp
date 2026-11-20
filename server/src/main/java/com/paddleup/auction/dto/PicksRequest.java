package com.paddleup.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * A franchise's accelerated-round nominations: unsold lot numbers, at most its open squad slots.
 * They replace its earlier ones, so an empty list clears them.
 */
public record PicksRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID intentId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Integer> lots) {}
