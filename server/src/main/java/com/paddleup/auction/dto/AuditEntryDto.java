package com.paddleup.auction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One audit row. {@code category} is the filter it belongs to (bids, passes, lifelines, rtm, admin
 * or other); {@code kind} is the event kind. {@code detail} holds only the fields a member may see
 * for that kind: a lifeline entry holds its type and nothing else, and no user id is ever shown.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEntryDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long seq,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant at,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String category,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String kind,
    UUID franchiseId,
    Integer lot,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> detail) {}
