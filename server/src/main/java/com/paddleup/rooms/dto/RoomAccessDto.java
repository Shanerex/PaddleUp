package com.paddleup.rooms.dto;

import java.util.UUID;

/**
 * What a member may do in a room, for modules that act on it. {@code franchiseId} is null for a
 * Commissioner who owns no franchise.
 */
public record RoomAccessDto(
    UUID roomId, RoomState state, boolean commissioner, UUID franchiseId, int franchiseCount) {}
