package com.paddleup.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A pass intent: the franchise sits out this one lot. */
public record PassRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID intentId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lot) {}
