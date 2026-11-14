package com.paddleup.scoring.model;

import java.math.BigDecimal;
import java.util.UUID;

/** One manual stat value for a room's pool player; {@code eventType} is "" for the stat itself. */
public record OverrideValue(UUID poolPlayerId, String stat, String eventType, BigDecimal value) {}
