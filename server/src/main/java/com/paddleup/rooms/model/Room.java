package com.paddleup.rooms.model;

import com.paddleup.rooms.dto.PackLabelsDto;
import com.paddleup.rooms.dto.RoomConfigDto;
import com.paddleup.rooms.dto.RoomState;
import java.time.Instant;
import java.util.UUID;

/** A room row. The config and the pack snapshot are stored as JSONB. */
public record Room(
    UUID id,
    RoomState state,
    UUID commissionerUserId,
    String packId,
    String packVersion,
    String presetId,
    PackLabelsDto packSnapshot,
    RoomConfigDto config,
    int configVersion,
    Instant createdAt,
    Instant updatedAt) {}
