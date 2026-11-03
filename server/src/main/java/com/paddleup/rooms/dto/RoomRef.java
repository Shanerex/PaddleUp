package com.paddleup.rooms.dto;

import java.util.UUID;

/** A room and the state it is in; what other modules get back from a state change. */
public record RoomRef(UUID id, RoomState state) {}
