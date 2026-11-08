package com.paddleup.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** A page of the log: events with seq above the requested one, oldest first. */
public record EventsDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AuctionEventDto> events,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long lastSeq,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean more,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant serverNow) {}
