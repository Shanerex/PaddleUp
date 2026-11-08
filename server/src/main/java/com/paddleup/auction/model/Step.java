package com.paddleup.auction.model;

/** One band of the increment ladder: from {@code from} upward, a bid rises by {@code step}. */
public record Step(long from, long step) {}
