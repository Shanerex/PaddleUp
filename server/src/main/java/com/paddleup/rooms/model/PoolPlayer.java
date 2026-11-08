package com.paddleup.rooms.model;

import com.paddleup.rooms.dto.PoolOrigin;
import java.util.List;
import java.util.UUID;

/** One row of a room's pool copy. {@code sourcePlayerId} is null for a player added by hand. */
public record PoolPlayer(
    UUID id,
    UUID sourcePlayerId,
    PoolOrigin origin,
    String name,
    String country,
    String role,
    List<String> tags,
    long basePrice,
    String set,
    String providerId,
    int position) {}
