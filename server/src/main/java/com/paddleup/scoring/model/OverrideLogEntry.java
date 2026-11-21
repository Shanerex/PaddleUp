package com.paddleup.scoring.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the append-only override log: a field saved or cleared. {@code oldValue} is null for a
 * first value and {@code newValue} is null for a clear.
 */
public record OverrideLogEntry(
    long seq,
    UUID roomId,
    UUID actor,
    Instant occurredAt,
    String matchId,
    UUID poolPlayerId,
    String stat,
    String eventType,
    BigDecimal oldValue,
    BigDecimal newValue) {}
