package com.paddleup.accounts.model;

import java.time.Instant;
import java.util.UUID;

/** Links an account to a sign-in provider's stable subject id. */
public record Identity(UUID userId, String provider, String subject, Instant createdAt) {}
