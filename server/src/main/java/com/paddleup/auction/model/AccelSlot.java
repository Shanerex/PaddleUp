package com.paddleup.auction.model;

/**
 * Where an accelerated-round lot stands: its 1-based {@code position} in the round's play order and
 * how many franchises picked it. Fixed when the round starts, so a replay gives the same values.
 */
public record AccelSlot(int position, int picks) {}
