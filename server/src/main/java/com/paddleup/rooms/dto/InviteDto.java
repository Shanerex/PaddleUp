package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** The room's active invite: the code, the link to share and when it stops working. */
public record InviteDto(
    @Schema(requiredMode = REQUIRED) String code,
    @Schema(requiredMode = REQUIRED) String link,
    @Schema(requiredMode = REQUIRED) Instant expiresAt) {}
