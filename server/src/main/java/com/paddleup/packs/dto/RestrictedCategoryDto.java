package com.paddleup.packs.dto;

/** A cap on how many squad players may carry {@code tag}. */
public record RestrictedCategoryDto(String id, String label, String tag, int max) {}
