package com.paddleup.auction.dto;

import java.util.List;

/** The rules the live auction froze at go-live. */
public record AuctionConfigDto(
    int openSeconds, int bidSeconds, int gapSeconds, List<StepDto> ladder) {}
