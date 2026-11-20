package com.paddleup.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * A page of the audit log, oldest first, read-only for every room member. {@code lastSeq} is the
 * seq to pass as {@code after} for the next page and {@code more} says whether there is one.
 */
public record AuditDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AuditEntryDto> entries,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long lastSeq,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean more,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant serverNow) {}
