package com.paddleup.rooms.dto;

import java.util.List;

/** The full set order the Commissioner wants, with the pool version they read. */
public record PoolSetsRequest(int version, List<String> order) {}
