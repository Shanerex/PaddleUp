package com.paddleup.auction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One logged event. {@code seq} is gap-free per auction, so a client that sees a jump refetches.
 * Intent ids and user ids are never included. {@code serverNow} is set on live broadcasts only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuctionEventDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long seq,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant at,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String kind,
    UUID franchiseId,
    Integer lot,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> payload,
    Instant serverNow) {}
