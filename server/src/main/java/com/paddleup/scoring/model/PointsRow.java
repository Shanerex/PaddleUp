package com.paddleup.scoring.model;

import com.paddleup.scoring.dto.PointsLineDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A stored result for one pool player in one match of one room. */
public record PointsRow(
    UUID poolPlayerId,
    BigDecimal total,
    List<PointsLineDto> lines,
    int ruleSetVersion,
    Instant computedAt) {}
