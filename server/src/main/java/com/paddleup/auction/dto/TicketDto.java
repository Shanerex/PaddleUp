package com.paddleup.auction.dto;

import java.time.Instant;

/** A single-use ticket for connecting to the auction socket without a session cookie. */
public record TicketDto(String ticket, Instant expiresAt) {}
