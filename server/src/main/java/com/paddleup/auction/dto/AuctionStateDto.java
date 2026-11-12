package com.paddleup.auction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full auction state for a connecting or reconnecting member. Deadlines are absolute UTC instants;
 * {@code serverNow} is the server clock when the state was read, so a client can correct for its
 * own clock. {@code lastSeq} is the seq of the last event the state includes. {@code ready} is the
 * lobby readiness (empty once the auction starts) and {@code nextBid} the only amount the next bid
 * on the open lot may have, from the increment ladder. {@code bidLock} is the viewer's own reason
 * they cannot bid that amount now, absent when they can (or have no franchise).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuctionStateDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String phase,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
    String startRefusal,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant serverNow,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long lastSeq,
    AuctionConfigDto config,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int lotCount,
    LotDto lot,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean lotOpen,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long currentBid,
    UUID leaderFranchiseId,
    Instant deadline,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> passedBy,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<StandingDto> standings,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Integer> unsold,
    Instant nextOpenAt,
    LastResultDto lastResult,
    LotDto nextLot,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> connected,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> ready,
    Long nextBid,
    BidLockDto bidLock) {}
