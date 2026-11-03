package com.paddleup.rooms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Asks what a code would join, without joining. */
public record JoinPreviewRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code) {}
