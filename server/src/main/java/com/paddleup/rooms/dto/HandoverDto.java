package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The new Commissioner, and whether the old one is still in the room: only with a franchise of
 * their own.
 */
public record HandoverDto(
    @Schema(requiredMode = REQUIRED) UserRefDto commissioner,
    @Schema(requiredMode = REQUIRED) boolean stillMember) {}
