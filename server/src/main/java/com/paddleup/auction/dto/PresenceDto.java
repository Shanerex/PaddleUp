package com.paddleup.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Who is in the lobby: franchises that currently have the auction open, and those that said they
 * are ready. Sent on the presence topic; not logged.
 */
public record PresenceDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> connected,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> ready) {}
