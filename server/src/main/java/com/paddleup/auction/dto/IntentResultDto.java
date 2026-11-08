package com.paddleup.auction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The answer to a bid or pass. {@code outcome} is {@code accepted} or {@code rejected}; a rejection
 * carries a {@code reason} and wrote no event. An accepted bid also carries the new {@code
 * deadline}. {@code serverNow} is the server clock when it answered.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntentResultDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String outcome,
    String reason,
    Long seq,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int lot,
    Long amount,
    Instant deadline,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant serverNow) {}
