package com.paddleup.auction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/** How the last lot closed; {@code franchiseId} is absent when it went unsold. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LastResultDto(int lot, boolean sold, UUID franchiseId, long amount) {}
