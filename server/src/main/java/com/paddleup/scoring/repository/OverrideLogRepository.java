package com.paddleup.scoring.repository;

import com.paddleup.scoring.model.OverrideLogEntry;
import com.paddleup.scoring.util.Sql;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The append-only override log: insert and select only. The database rejects anything else. */
@Repository
public class OverrideLogRepository {

  private final JdbcClient jdbc;

  public OverrideLogRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Appends the entry; its {@code seq} is assigned by the database and ignored here. */
  public void append(OverrideLogEntry e) {
    jdbc.sql(
            """
            INSERT INTO scoring_override_log (room_id, actor, occurred_at, match_id,
                                              pool_player_id, stat, event_type, old_value,
                                              new_value)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .params(
            e.roomId(),
            e.actor(),
            Sql.utc(e.occurredAt()),
            e.matchId(),
            e.poolPlayerId(),
            e.stat(),
            e.eventType(),
            e.oldValue(),
            e.newValue())
        .update();
  }

  /**
   * The room's entries with {@code seq} above {@code after}, oldest first, at most {@code limit}.
   */
  public List<OverrideLogEntry> page(UUID roomId, long after, int limit) {
    return jdbc.sql(
            "SELECT * FROM scoring_override_log WHERE room_id = ? AND seq > ?"
                + " ORDER BY seq LIMIT ?")
        .params(roomId, after, limit)
        .query(
            (rs, n) ->
                new OverrideLogEntry(
                    rs.getLong("seq"),
                    rs.getObject("room_id", UUID.class),
                    rs.getObject("actor", UUID.class),
                    Sql.instant(rs, "occurred_at"),
                    rs.getString("match_id"),
                    rs.getObject("pool_player_id", UUID.class),
                    rs.getString("stat"),
                    rs.getString("event_type"),
                    rs.getBigDecimal("old_value"),
                    rs.getBigDecimal("new_value")))
        .list();
  }
}
