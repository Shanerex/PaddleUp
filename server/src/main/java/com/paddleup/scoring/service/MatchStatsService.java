package com.paddleup.scoring.service;

import com.paddleup.packs.dto.StatDto;
import com.paddleup.rooms.dto.ScoringInputsDto;
import com.paddleup.rooms.service.ScoringRoomPort;
import com.paddleup.scoring.dto.ProviderStatLineDto;
import com.paddleup.scoring.model.OverrideValue;
import com.paddleup.scoring.model.ProviderValue;
import com.paddleup.scoring.repository.MatchStatsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write side for fixtures and stats. It is a service API only (no REST): the ingestion work
 * builds on it. Provider stats are stored once per season dataset and shared by every room on it; a
 * manual value is stored for one room and beats the provider value.
 */
@Service
public class MatchStatsService {

  /** Largest stat value a manual entry may carry. */
  static final BigDecimal VALUE_MAX = BigDecimal.valueOf(1_000_000);

  static final int VALUE_DECIMALS_MAX = 4;

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
    if (matchId == null || matchId.isBlank() || startsAt == null) {
      throw new IllegalArgumentException("A match needs an id and a start");
    }
    stats.upsertMatch(datasetId, matchId, startsAt);
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
                          line.seasonPlayerId(), stat, EffectiveStats.NO_EVENT, value)));
      line.events()
          .forEach(
              (stat, byType) ->
                  byType.forEach(
                      (type, count) ->
                          values.add(new ProviderValue(line.seasonPlayerId(), stat, type, count))));
    }
    return values;
  }

  /**
   * Saves one manual value for a pool player of the room. The stat and event type must belong to
   * the room's pack vocabulary and the value must be a plain amount. Then recomputes that room and
   * match.
   */
  @Transactional
  public void putOverride(
      UUID roomId,
      String matchId,
      UUID poolPlayerId,
      String stat,
      String eventType,
      BigDecimal value) {
    ScoringInputsDto inputs = roomPort.ruleInputs(roomId);
    if (inputs.datasetId() != null) {
      requireMatch(inputs.datasetId(), matchId);
    }
    if (roomPort.players(roomId).stream().noneMatch(p -> p.poolPlayerId().equals(poolPlayerId))) {
      throw new IllegalArgumentException("Not a player of this room");
    }
    StatDto declared =
        inputs.stats().stream()
            .filter(s -> s.id().equals(stat))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Not a stat of this room"));
    String type = eventType == null ? EffectiveStats.NO_EVENT : eventType;
    if (!type.isEmpty() && !declared.eventTypes().contains(type)) {
      throw new IllegalArgumentException("Not an event type of the stat");
    }
    if (value == null
        || value.signum() < 0
        || value.compareTo(VALUE_MAX) > 0
        || value.scale() > VALUE_DECIMALS_MAX) {
      throw new IllegalArgumentException("A stat value must be 0 to 1000000 with 4 decimals");
    }
    stats.upsertOverride(roomId, matchId, new OverrideValue(poolPlayerId, stat, type, value));
    scoring.recompute(roomId, matchId);
  }

  private void requireMatch(UUID datasetId, String matchId) {
    if (!stats.matchExists(datasetId, matchId)) {
      throw new IllegalArgumentException("Unknown match");
    }
  }
}
