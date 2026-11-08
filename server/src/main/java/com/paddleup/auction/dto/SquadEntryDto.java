package com.paddleup.auction.dto;

import java.util.UUID;

/** A player a franchise won and what it paid. */
public record SquadEntryDto(int lot, UUID playerId, long price) {}
