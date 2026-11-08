package com.paddleup.auction.model;

import java.util.UUID;

/** A player a franchise won, at the price it paid. */
public record SquadEntry(int lot, UUID playerId, long price) {}
