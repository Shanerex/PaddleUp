package com.paddleup.accounts.dto;

/** Fields left null are not changed. */
public record UpdateProfileRequest(String displayName, String avatar) {}
