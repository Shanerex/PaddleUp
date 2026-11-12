package com.paddleup.scoring.repository;

import com.paddleup.scoring.model.OverrideValue;
import com.paddleup.scoring.model.ProviderValue;
import com.paddleup.scoring.util.Sql;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Matches and provider stats (once per season dataset, shared by every room on it) and the sparse
 * manual overrides of one room.
 */
@Repository
public class MatchStatsRepository {

  private final JdbcClient jdbc;
  private final JdbcTemplate template;

  public MatchStatsRepository(JdbcClient jdbc, JdbcTemplate template) {
    this.jdbc = jdbc;
    this.template = template;
  }

  /** Adds the match or moves its scheduled start. */
  public void upsertMatch(UUID datasetId, String matchId, Instant startsAt) {
    jdbc.sql(
            """
            INSERT INTO scoring_matches (dataset_id, match_id, starts_at) VALUES (?, ?, ?)
            ON CONFLICT (dataset_id, match_id) DO UPDATE SET starts_at = EXCLUDED.starts_at
            """)
        .params(datasetId, matchId, Sql.utc(startsAt))
        .update();
  }

  public boolean matchExists(UUID datasetId, String matchId) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM scoring_matches WHERE dataset_id = ? AND match_id = ?)")
        .params(datasetId, matchId)
        .query(Boolean.class)
        .single();
  }

  /** The earliest scheduled start among the dataset's matches; empty when it has none. */
  public Optional<Instant> earliestStart(UUID datasetId) {
    return jdbc.sql(
            "SELECT starts_at FROM scoring_matches WHERE dataset_id = ?"
                + " ORDER BY starts_at LIMIT 1")
        .param(datasetId)
        .query((rs, n) -> Sql.instant(rs, "starts_at"))
        .optional();
  }

  /** Replaces every provider row of the match with {@code values}. */
  public void replaceProviderValues(UUID datasetId, String matchId, List<ProviderValue> values) {
    jdbc.sql("DELETE FROM scoring_stat_values WHERE dataset_id = ? AND match_id = ?")
        .params(datasetId, matchId)
        .update();
    template.batchUpdate(
        "INSERT INTO scoring_stat_values (dataset_id, match_id, season_player_id, stat,"
            + " event_type, value) VALUES (?, ?, ?, ?, ?, ?)",
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            ProviderValue v = values.get(i);
            ps.setObject(1, datasetId);
            ps.setString(2, matchId);
            ps.setObject(3, v.seasonPlayerId());
            ps.setString(4, v.stat());
            ps.setString(5, v.eventType());
            ps.setBigDecimal(6, v.value());
          }

          @Override
          public int getBatchSize() {
            return values.size();
          }
        });
  }

  public List<ProviderValue> providerValues(UUID datasetId, String matchId) {
    return jdbc.sql(
            "SELECT season_player_id, stat, event_type, value FROM scoring_stat_values"
                + " WHERE dataset_id = ? AND match_id = ?"
                + " ORDER BY season_player_id, stat, event_type")
        .params(datasetId, matchId)
        .query(
            (rs, n) ->
                new ProviderValue(
                    rs.getObject("season_player_id", UUID.class),
                    rs.getString("stat"),
                    rs.getString("event_type"),
                    rs.getBigDecimal("value")))
        .list();
  }

  /** Saves one manual value, replacing an earlier one for the same key. */
  public void upsertOverride(UUID roomId, String matchId, OverrideValue v) {
    jdbc.sql(
            """
            INSERT INTO scoring_stat_overrides (room_id, match_id, pool_player_id, stat,
                                                event_type, value)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (room_id, match_id, pool_player_id, stat, event_type)
            DO UPDATE SET value = EXCLUDED.value
            """)
        .params(roomId, matchId, v.poolPlayerId(), v.stat(), v.eventType(), v.value())
        .update();
  }

  public List<OverrideValue> overrides(UUID roomId, String matchId) {
    return jdbc.sql(
            "SELECT pool_player_id, stat, event_type, value FROM scoring_stat_overrides"
                + " WHERE room_id = ? AND match_id = ?"
                + " ORDER BY pool_player_id, stat, event_type")
        .params(roomId, matchId)
        .query(
            (rs, n) ->
                new OverrideValue(
                    rs.getObject("pool_player_id", UUID.class),
                    rs.getString("stat"),
                    rs.getString("event_type"),
                    rs.getBigDecimal("value")))
        .list();
  }

  /**
   * Ids of the matches that have any stat for this room: provider rows on its dataset (when it has
   * one) or a manual override of its own.
   */
  public List<String> scoredMatches(UUID roomId, UUID datasetId) {
    return jdbc.sql(
            """
            SELECT match_id FROM scoring_stat_overrides WHERE room_id = ?
            UNION
            SELECT match_id FROM scoring_stat_values WHERE dataset_id = ?::uuid
            ORDER BY match_id
            """)
        .params(roomId, datasetId)
        .query(String.class)
        .list();
  }
}
