package com.paddleup.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A bid intent. {@code intentId} is chosen by the client and makes a resend harmless; {@code lot}
 * is the lot number being bid on and {@code amount} the exact next price.
 */
public record BidRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID intentId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer lot,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long amount) {}
