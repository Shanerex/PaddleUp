package com.paddleup.rooms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Redeems an invite code and names the franchise that joins with it. */
public record JoinRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FranchiseRequest franchise) {}
