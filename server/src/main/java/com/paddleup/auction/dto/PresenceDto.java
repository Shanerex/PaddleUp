package com.paddleup.auction.dto;

import java.util.List;
import java.util.UUID;

/** Franchises that currently have the auction open. Sent on the presence topic; not logged. */
public record PresenceDto(List<UUID> connected) {}
