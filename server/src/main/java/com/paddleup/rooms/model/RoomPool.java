package com.paddleup.rooms.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The header of a room's pool copy: its source, its version and its set order. */
public record RoomPool(
    UUID roomId,
    UUID datasetId,
    int datasetVersion,
    int sourceCount,
    int poolVersion,
    List<String> setOrder,
    Instant createdAt,
    Instant updatedAt) {}
