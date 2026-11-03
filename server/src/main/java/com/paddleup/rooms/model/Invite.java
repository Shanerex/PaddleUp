package com.paddleup.rooms.model;

import java.time.Instant;
import java.util.UUID;

/** An invite code row. It is active until revoked or past {@code expiresAt}. */
public record Invite(
    UUID id,
    UUID roomId,
    String code,
    UUID issuedBy,
    Instant issuedAt,
    Instant expiresAt,
    Instant revokedAt) {

  public boolean activeAt(Instant now) {
    return revokedAt == null && now.isBefore(expiresAt);
  }
}
