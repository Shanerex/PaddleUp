package com.paddleup.auction.model;

import java.time.Instant;
import java.util.UUID;

/** The boot-discovery row of an auction; the event log stays the source of truth. */
public record AuctionHeader(
    UUID roomId,
    Phase phase,
    Instant startsAt,
    UUID scheduledBy,
    Instant startRefusedAt,
    long lastSeq) {}
