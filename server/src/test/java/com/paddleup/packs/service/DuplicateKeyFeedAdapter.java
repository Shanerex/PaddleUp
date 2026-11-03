package com.paddleup.packs.service;

import com.paddleup.packs.dto.FixtureDto;
import com.paddleup.packs.dto.StatSheetDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** A second adapter reusing the stub key, to prove duplicate keys fail startup. */
public class DuplicateKeyFeedAdapter implements DataAdapter {

  @Override
  public String key() {
    return "stub-feed";
  }

  @Override
  public List<FixtureDto> listFixtures(Instant from, Instant to) {
    return List.of();
  }

  @Override
  public StatSheetDto fetchStats(String externalMatchId) {
    return new StatSheetDto(externalMatchId, List.of());
  }

  @Override
  public Optional<String> mapPlayerId(String externalPlayerId) {
    return Optional.empty();
  }
}
