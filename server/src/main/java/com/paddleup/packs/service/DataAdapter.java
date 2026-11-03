package com.paddleup.packs.service;

import com.paddleup.packs.dto.FixtureDto;
import com.paddleup.packs.dto.StatSheetDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A source of fixtures and completed-match stats for a pack. Implement it as a Spring bean; a pack
 * selects it by setting its {@code adapterKey} equal to {@link #key()}. Keys must be unique.
 */
public interface DataAdapter {

  /** The key packs use to select this adapter. */
  String key();

  /** Fixtures starting within the window. */
  List<FixtureDto> listFixtures(Instant from, Instant to);

  /** Stats of a completed match, in the pack's stat schema. */
  StatSheetDto fetchStats(String externalMatchId);

  /** The internal player id for an external one, when known. */
  Optional<String> mapPlayerId(String externalPlayerId);
}
