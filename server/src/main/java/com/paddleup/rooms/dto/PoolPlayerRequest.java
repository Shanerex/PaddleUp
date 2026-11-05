package com.paddleup.rooms.dto;

import java.util.List;

/**
 * An add or an edit of one pool player, with the pool version the caller read. {@code set} must be
 * a set already in the pool.
 */
public record PoolPlayerRequest(
    int version,
    String name,
    String country,
    String role,
    List<String> tags,
    Long basePrice,
    String set,
    String providerId) {}
