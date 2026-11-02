package com.paddleup.accounts.model;

import java.time.Instant;
import java.util.UUID;

/** A single-use email sign-in token. Link and code secrets are stored hashed only. */
public record LoginToken(
    UUID id,
    String email,
    String linkHash,
    String codeHash,
    Instant expiresAt,
    Instant usedAt,
    int attempts,
    Instant createdAt) {}
