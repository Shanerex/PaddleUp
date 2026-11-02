package com.paddleup.packs.dto;

import java.time.Instant;
import java.util.List;

/** A scheduled match from a data adapter. Times are UTC instants. */
public record FixtureDto(
    String externalMatchId, Instant startsAt, List<String> participants, String status) {
  public FixtureDto {
    participants = participants == null ? List.of() : List.copyOf(participants);
  }
}
