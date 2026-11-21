package com.paddleup.scoring.model;

import java.time.Instant;

/**
 * One dataset match as one room sees it: how many provider rows it has and how many players carry a
 * manual value in that room. {@code label} may be null.
 */
public record MatchSummary(
    String matchId, Instant startsAt, String label, int providerRows, int overridePlayers) {}
