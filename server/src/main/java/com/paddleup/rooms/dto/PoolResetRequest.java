package com.paddleup.rooms.dto;

/** Reset the pool to the latest season data. {@code version} is 0 when the room has no copy. */
public record PoolResetRequest(int version) {}
