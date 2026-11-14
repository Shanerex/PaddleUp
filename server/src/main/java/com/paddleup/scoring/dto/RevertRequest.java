package com.paddleup.scoring.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** Back to the pack default. {@code version} is the rule set version the editor started from. */
public record RevertRequest(@Schema(requiredMode = REQUIRED) Integer version) {}
