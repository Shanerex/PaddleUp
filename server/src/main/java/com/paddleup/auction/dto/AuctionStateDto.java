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
 * on the open lot may have, from the increment ladder. {@code bidLimits} are the viewer's own bid
 * limits on the open lot, in the order the server checks them: the first that refuses an amount is
 * why the viewer cannot bid it. They hold for the whole lot, so a bid patch need not reread them.
 *
 * <p>{@code heldMs} is the time frozen by pause or adjourn (0 otherwise), {@code resumeAt} the
 * adjourn time and {@code liveAt} the end of the resume lobby. {@code round} is {@code main} or
 * {@code accelerated}. For the accelerated round every member sees {@code unsoldPool} (the cards),
 * {@code pickCounts} and {@code waiting} (franchises that have not sent picks); {@code myPicks} and
 * {@code openSlots} are the viewer's own, and {@code openSlots} is absent when the viewer owns no
 * franchise.
 *
 * <p>Once the accelerated round has started, {@code accelSize} is how many lots it plays (absent
 * before), and for the lot shown {@code accelPosition} is its 1-based place in that order and
 * {@code lotPicks} how many franchises picked it (both absent for a main-round lot). They come from
 * the log, so a restart gives the same values.
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
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<BidLockDto> bidLimits,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long heldMs,
    Instant resumeAt,
    Instant liveAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String round,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<LotDto> unsoldPool,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PickCountDto> pickCounts,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Integer> myPicks,
    Integer openSlots,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> waiting,
    Integer accelPosition,
    Integer accelSize,
    Integer lotPicks) {}
