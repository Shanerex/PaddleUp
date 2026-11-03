package com.paddleup.packs.dto;

import java.math.BigDecimal;

/** A player counts as having appeared when {@code stat} is at least {@code atLeast}. */
public record AppearanceRuleDto(String stat, BigDecimal atLeast) {}
