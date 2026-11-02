package com.paddleup.accounts.dto;

/** Either {@code token} (from the link) or {@code email} with {@code code}. */
public record EmailVerifyRequest(String token, String email, String code) {}
