package com.paddleup.accounts.model;

import java.time.Instant;
import java.util.UUID;

/** A person's account. Holds no room data; membership lives in the rooms module. */
public record User(
    UUID id,
    String email,
    String displayName,
    String avatar,
    String googlePictureUrl,
    Instant createdAt,
    Instant updatedAt) {}
