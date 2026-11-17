package com.paddleup.auction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One limit on what the viewer may bid on the open lot: a rule code ({@code purse}, {@code
 * squad_full}, {@code quota}, {@code no_room} or {@code reserve}), with the pack role id or
 * restricted-category id it concerns and, for a quota, the cap frozen at go-live. {@code above} is
 * the highest bid it still allows; absent, it allows none. The client words it from the room's pack
 * labels.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BidLockDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
    String role,
    String category,
    Integer cap,
    Long above) {}
