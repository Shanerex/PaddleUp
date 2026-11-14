package com.paddleup.auction.model;

/**
 * Why a franchise cannot bid on the open lot: a {@link Reason} code, plus the pack role or
 * restricted category it concerns when there is one.
 */
public record BidLock(String reason, String role, String category) {}
