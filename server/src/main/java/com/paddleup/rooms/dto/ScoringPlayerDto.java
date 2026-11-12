package com.paddleup.rooms.dto;

import java.util.UUID;

/**
 * One player of a room's pool as scoring sees it. {@code sourcePlayerId} is the season dataset
 * player the row was copied from; it is null for a player the Commissioner added by hand.
 */
public record ScoringPlayerDto(UUID poolPlayerId, UUID sourcePlayerId, String role) {}
