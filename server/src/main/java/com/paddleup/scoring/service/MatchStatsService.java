package com.paddleup.scoring.service;

import com.paddleup.rooms.service.ScoringRoomPort;
import com.paddleup.scoring.dto.ProviderStatLineDto;
import com.paddleup.scoring.model.ProviderValue;
import com.paddleup.scoring.repository.MatchStatsRepository;
import com.paddleup.scoring.util.StatValues;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write side for fixtures and stats. It is a service API only (no REST): the ingestion work
 * builds on it. Provider stats are stored once per season dataset and shared by every room on it; a
 * manual value is stored for one room and beats the provider value (see ManualStatsService).
 */
@Service
public class MatchStatsService {

  static final int LABEL_MAX = 60;

  private final MatchStatsRepository stats;
  private final ScoringRoomPort roomPort;
  private final ScoringService scoring;

  MatchStatsService(MatchStatsRepository stats, ScoringRoomPort roomPort, ScoringService scoring) {
    this.stats = stats;
    this.roomPort = roomPort;
    this.scoring = scoring;
  }

  /** Adds the match to the dataset or moves its scheduled start. */
  @Transactional
  public void upsertMatch(UUID datasetId, String matchId, Instant startsAt) {
    upsertMatch(datasetId, matchId, startsAt, null);
  }

  /**
   * As {@link #upsertMatch(UUID, String, Instant)} with an optional label shown after the match
   * number ("Match 3 · label"); a null or blank label keeps the one the match has.
   */
  @Transactional
  public void upsertMatch(UUID datasetId, String matchId, Instant startsAt, String label) {
    if (matchId == null || matchId.isBlank() || startsAt == null) {
      throw new IllegalArgumentException("A match needs an id and a start");
    }
    String text = label == null || label.isBlank() ? null : label.strip();
    if (text != null && text.length() > LABEL_MAX) {
      throw new IllegalArgumentException("A match label is at most " + LABEL_MAX + " characters");
    }
    stats.upsertMatch(datasetId, matchId, startsAt, text);
  }

  /**
   * Replaces the dataset match's provider rows with these lines, then recomputes the match for
   * every room on the dataset in the same transaction.
   */
  @Transactional
  public void putProviderStats(UUID datasetId, String matchId, List<ProviderStatLineDto> lines) {
    requireMatch(datasetId, matchId);
    stats.replaceProviderValues(datasetId, matchId, providerValues(lines));
    for (UUID roomId : roomPort.roomsOnDataset(datasetId)) {
      scoring.recompute(roomId, matchId);
    }
  }

  private static List<ProviderValue> providerValues(List<ProviderStatLineDto> lines) {
    List<ProviderValue> values = new ArrayList<>();
    for (ProviderStatLineDto line : lines) {
      line.stats()
          .forEach(
              (stat, value) ->
                  values.add(
                      new ProviderValue(
                          line.seasonPlayerId(),
                          stat,
                          EffectiveStats.NO_EVENT,
                          StatValues.normalise(value))));
      line.events()
          .forEach(
              (stat, byType) ->
                  byType.forEach(
                      (type, count) ->
                          values.add(
                              new ProviderValue(
                                  line.seasonPlayerId(),
                                  stat,
                                  type,
                                  StatValues.normalise(count)))));
    }
    return values;
  }

  private void requireMatch(UUID datasetId, String matchId) {
    if (!stats.matchExists(datasetId, matchId)) {
      throw new IllegalArgumentException("Unknown match");
    }
  }
}
