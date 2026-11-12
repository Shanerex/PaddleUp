package com.paddleup.scoring.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.scoring.dto.RuleDiffDto;
import com.paddleup.scoring.model.RuleLogEntry;
import com.paddleup.scoring.model.RuleLogKind;
import com.paddleup.scoring.util.Sql;
import com.paddleup.scoring.util.StoredJson;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The append-only rule change log: insert and select only. The database rejects anything else. */
@Repository
public class RuleLogRepository {

  private final JdbcClient jdbc;
  private final StoredJson json;

  public RuleLogRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = new StoredJson(json);
  }

  /** Appends the entry; its {@code seq} is assigned by the database and ignored here. */
  public void append(RuleLogEntry entry) {
    jdbc.sql(
            """
            INSERT INTO scoring_rule_log (room_id, kind, actor, occurred_at, reason, attested,
                                          before, after, diff)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
            """)
        .params(
            entry.roomId(),
            entry.kind().value(),
            entry.actor(),
            Sql.utc(entry.occurredAt()),
            entry.reason(),
            entry.attested(),
            json.write(entry.before()),
            json.write(entry.after()),
            json.write(entry.diff()))
        .update();
  }

  /** The room's entries, oldest first. */
  public List<RuleLogEntry> list(UUID roomId) {
    return jdbc.sql("SELECT * FROM scoring_rule_log WHERE room_id = ? ORDER BY seq")
        .param(roomId)
        .query(
            (rs, n) ->
                new RuleLogEntry(
                    rs.getLong("seq"),
                    rs.getObject("room_id", UUID.class),
                    RuleLogKind.fromValue(rs.getString("kind")),
                    rs.getObject("actor", UUID.class),
                    Sql.instant(rs, "occurred_at"),
                    rs.getString("reason"),
                    rs.getBoolean("attested"),
                    json.read(rs.getString("before"), RuleSetDto.class),
                    json.read(rs.getString("after"), RuleSetDto.class),
                    json.read(rs.getString("diff"), RuleDiffDto.class)))
        .list();
  }
}
