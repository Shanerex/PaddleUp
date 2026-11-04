package com.paddleup.rooms.model;

import java.time.Instant;
import java.util.UUID;

/** One manager's team in a room. */
public record Franchise(
    UUID id,
    UUID roomId,
    UUID ownerUserId,
    String name,
    String colour,
    String monogram,
    Instant createdAt,
    Instant updatedAt) {}
