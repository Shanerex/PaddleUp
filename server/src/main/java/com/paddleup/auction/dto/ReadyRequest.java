package com.paddleup.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** The lobby ready toggle: an intent, the server decides what shows. */
public record ReadyRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean ready) {}
