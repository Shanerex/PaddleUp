package com.paddleup.scoring.model;

import com.paddleup.packs.dto.RuleSetDto;
import java.time.Instant;
import java.util.UUID;

/**
 * A room's scoring rule row. {@code rules} is null while the room plays its pack-default snapshot.
 * {@code version} is the compare-and-set counter, 0 until the first change; {@code frozenAt} is
 * null until the freeze has been seen.
 */
public record RuleSetRow(
    UUID roomId,
    RuleSetDto rules,
    int version,
    Instant frozenAt,
    Instant updatedAt,
    UUID updatedBy) {}
