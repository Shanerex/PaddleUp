package com.paddleup.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** How many franchises picked one unsold lot in the accelerated round. */
public record PickCountDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int lot,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int count) {}
