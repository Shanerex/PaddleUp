package com.paddleup.scoring.model;

import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.scoring.dto.RuleDiffDto;
import java.time.Instant;
import java.util.UUID;

/** One row of the append-only rule change log; {@code before} and {@code after} are whole sets. */
public record RuleLogEntry(
    long seq,
    UUID roomId,
    RuleLogKind kind,
    UUID actor,
    Instant occurredAt,
    String reason,
    boolean attested,
    RuleSetDto before,
    RuleSetDto after,
    RuleDiffDto diff) {}
