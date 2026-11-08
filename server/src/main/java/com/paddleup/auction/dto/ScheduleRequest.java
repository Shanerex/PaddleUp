package com.paddleup.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** The start instant, in UTC. Must be in the future. */
public record ScheduleRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt) {}
