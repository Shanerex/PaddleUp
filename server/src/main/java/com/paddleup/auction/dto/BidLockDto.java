package com.paddleup.auction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Why the viewer cannot bid on the open lot: a rule code ({@code squad_full}, {@code quota} or
 * {@code reserve}), with the pack role id or restricted-category id it concerns when there is one.
 * The client words it from the room's pack labels.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BidLockDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
    String role,
    String category) {}
