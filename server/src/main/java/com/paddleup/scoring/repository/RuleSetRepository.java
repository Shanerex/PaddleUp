package com.paddleup.scoring.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.scoring.model.RuleSetRow;
import com.paddleup.scoring.util.Sql;
import com.paddleup.scoring.util.StoredJson;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * A room's rule row. It is created lazily on the first write and locked for the rest of the
 * transaction, which is what serialises two edits and an edit against the freeze.
 */
@Repository
public class RuleSetRepository {

  private final JdbcClient jdbc;
  private final StoredJson json;
  private final RowMapper<RuleSetRow> mapper;

  public RuleSetRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = new StoredJson(json);
    this.mapper = this::map;
  }

  private RuleSetRow map(java.sql.ResultSet rs, int row) throws SQLException {
    String rules = rs.getString("rules");
    return new RuleSetRow(
        rs.getObject("room_id", UUID.class),
        rules == null ? null : json.read(rules, RuleSetDto.class),
        rs.getInt("version"),
        Sql.instant(rs, "frozen_at"),
        Sql.instant(rs, "updated_at"),
        rs.getObject("updated_by", UUID.class));
  }

  /** Creates the row (pack default, version 0) unless one exists. */
  public void insertIfAbsent(UUID roomId, Instant now) {
    jdbc.sql(
            "INSERT INTO scoring_rule_sets (room_id, updated_at) VALUES (?, ?)"
                + " ON CONFLICT (room_id) DO NOTHING")
        .params(roomId, Sql.utc(now))
        .update();
  }

  public Optional<RuleSetRow> find(UUID roomId) {
    return jdbc.sql("SELECT * FROM scoring_rule_sets WHERE room_id = ?")
        .param(roomId)
        .query(mapper)
        .optional();
  }

  /** Reads the row and holds its lock until the transaction ends. */
  public Optional<RuleSetRow> lock(UUID roomId) {
    return jdbc.sql("SELECT * FROM scoring_rule_sets WHERE room_id = ? FOR UPDATE")
        .param(roomId)
        .query(mapper)
        .optional();
  }

  /**
   * Saves {@code rules} (null = back to the pack default) and bumps the version, only if the stored
   * version is still {@code from}.
   */
  public boolean update(UUID roomId, RuleSetDto rules, int from, UUID by, Instant now) {
    return jdbc.sql(
                """
                UPDATE scoring_rule_sets
                SET rules = ?::jsonb, version = version + 1, updated_at = ?, updated_by = ?
                WHERE room_id = ? AND version = ?
                """)
            .params(rules == null ? null : json.write(rules), Sql.utc(now), by, roomId, from)
            .update()
        == 1;
  }

  /** Records the freeze once; an already frozen row keeps its first instant. */
  public void markFrozen(UUID roomId, Instant at) {
    jdbc.sql("UPDATE scoring_rule_sets SET frozen_at = ? WHERE room_id = ? AND frozen_at IS NULL")
        .params(Sql.utc(at), roomId)
        .update();
  }
}
