package com.paddleup.rooms.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A row of the append-only room log. */
public record RoomLogEntry(
    long id,
    UUID roomId,
    LogKind kind,
    UUID actorUserId,
    Map<String, Object> detail,
    Instant occurredAt) {}
