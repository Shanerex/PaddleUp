package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A room member: the Commissioner or a franchise owner. {@code franchise} is null for a
 * Commissioner who only administers the room. {@code role} is {@code commissioner} or {@code
 * manager}.
 */
public record MemberDto(
    @Schema(requiredMode = REQUIRED) UUID userId,
    @Schema(requiredMode = REQUIRED) String displayName,
    @Schema(requiredMode = REQUIRED) String avatar,
    @Schema(requiredMode = REQUIRED) String role,
    @Schema(nullable = true) FranchiseDto franchise) {}
