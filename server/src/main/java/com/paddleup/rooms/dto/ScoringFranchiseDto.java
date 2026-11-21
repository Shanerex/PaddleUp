package com.paddleup.rooms.dto;

import java.util.UUID;

/** One franchise of a room as scoring shows it: its name, short name (monogram) and colour key. */
public record ScoringFranchiseDto(UUID id, String name, String shortName, String colour) {}
