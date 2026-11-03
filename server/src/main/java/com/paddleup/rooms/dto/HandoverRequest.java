package com.paddleup.rooms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Hands the Commissioner role to the owner of this franchise. */
public record HandoverRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID franchiseId) {}
