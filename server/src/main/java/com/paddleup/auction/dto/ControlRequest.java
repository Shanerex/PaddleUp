package com.paddleup.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A Commissioner control. {@code intentId} is chosen by the client and makes a resend harmless.
 * {@code action} is one of pause, resume, skip, reopen, adjourn, change_time, resume_now,
 * start_now, open_round, end_auction or end_round. {@code lot} names the lot for skip and reopen,
 * {@code reason} is required by reopen and {@code resumeAt} by adjourn and change_time.
 */
public record ControlRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID intentId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String action,
    Integer lot,
    String reason,
    Instant resumeAt) {}
