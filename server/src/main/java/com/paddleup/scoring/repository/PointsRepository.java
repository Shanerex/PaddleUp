package com.paddleup.scoring.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.scoring.dto.PointsLineDto;
import com.paddleup.scoring.model.PointsRow;
import com.paddleup.scoring.util.Sql;
import com.paddleup.scoring.util.StoredJson;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Stored points per room, match and pool player. A recompute upserts the new rows and deletes the
 * rest, so two recomputes that meet never leave a duplicate or a missing row.
 */
@Repository
public class PointsRepository {

  private static final TypeReference<List<PointsLineDto>> LINES = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final JdbcTemplate template;
  private final StoredJson json;

  public PointsRepository(JdbcClient jdbc, JdbcTemplate template, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.template = template;
    this.json = new StoredJson(mapper);
  }

  /** Makes the stored rows of the room's match exactly {@code rows}. */
  public void replace(UUID roomId, String matchId, List<PointsRow> rows) {
    template.batchUpdate(
        """
        INSERT INTO scoring_points (room_id, match_id, pool_player_id, total, lines,
                                    rule_set_version, computed_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
        ON CONFLICT (room_id, match_id, pool_player_id) DO UPDATE
        SET total = EXCLUDED.total, lines = EXCLUDED.lines,
            rule_set_version = EXCLUDED.rule_set_version, computed_at = EXCLUDED.computed_at
        """,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            PointsRow row = rows.get(i);
            ps.setObject(1, roomId);
            ps.setString(2, matchId);
            ps.setObject(3, row.poolPlayerId());
            ps.setBigDecimal(4, row.total());
            ps.setString(5, json.write(row.lines()));
            ps.setInt(6, row.ruleSetVersion());
            ps.setObject(7, Sql.utc(row.computedAt()));
          }

          @Override
          public int getBatchSize() {
            return rows.size();
          }
        });
    UUID[] keep = rows.stream().map(PointsRow::poolPlayerId).toArray(UUID[]::new);
    template.update(
        con -> {
          PreparedStatement ps =
              con.prepareStatement(
                  "DELETE FROM scoring_points WHERE room_id = ? AND match_id = ?"
                      + " AND NOT (pool_player_id = ANY (?))");
          ps.setObject(1, roomId);
          ps.setString(2, matchId);
          Array ids = con.createArrayOf("uuid", keep);
          ps.setArray(3, ids);
          return ps;
        });
  }

  /** The room's rows for one match, by pool player id. */
  public List<PointsRow> forMatch(UUID roomId, String matchId) {
    return jdbc.sql(
            "SELECT * FROM scoring_points WHERE room_id = ? AND match_id = ?"
                + " ORDER BY pool_player_id")
        .params(roomId, matchId)
        .query(
            (rs, n) ->
                new PointsRow(
                    rs.getObject("pool_player_id", UUID.class),
                    rs.getBigDecimal("total"),
                    json.read(rs.getString("lines"), LINES),
                    rs.getInt("rule_set_version"),
                    Sql.instant(rs, "computed_at")))
        .list();
  }
}
