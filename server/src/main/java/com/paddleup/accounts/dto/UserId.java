package com.paddleup.accounts.dto;

import java.util.UUID;

/** Identifier other modules use to reference an account. */
public record UserId(UUID value) {}
