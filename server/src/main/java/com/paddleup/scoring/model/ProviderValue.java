package com.paddleup.scoring.model;

import java.math.BigDecimal;
import java.util.UUID;

/** One provider stat value for a dataset player; {@code eventType} is "" for the stat itself. */
public record ProviderValue(UUID seasonPlayerId, String stat, String eventType, BigDecimal value) {}
