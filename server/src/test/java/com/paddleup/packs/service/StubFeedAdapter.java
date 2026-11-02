package com.paddleup.packs.service;

import com.paddleup.packs.dto.FixtureDto;
import com.paddleup.packs.dto.StatLineDto;
import com.paddleup.packs.dto.StatSheetDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test adapter for the stub pack: one fixture, one stat sheet with typed events. */
public class StubFeedAdapter implements DataAdapter {

  @Override
  public String key() {
    return "stub-feed";
  }

  @Override
  public List<FixtureDto> listFixtures(Instant from, Instant to) {
    return List.of(new FixtureDto("m-1", from, List.of("home", "away"), "scheduled"));
  }

  @Override
  public StatSheetDto fetchStats(String externalMatchId) {
    return new StatSheetDto(
        externalMatchId,
        List.of(
            new StatLineDto(
                "ext-7",
                Map.of("goals", new BigDecimal("2"), "assists", BigDecimal.ONE),
                Map.of("goals", Map.of("open-play", 1, "penalty", 1)))));
  }

  @Override
  public Optional<String> mapPlayerId(String externalPlayerId) {
    return "ext-7".equals(externalPlayerId) ? Optional.of("player-7") : Optional.empty();
  }
}
