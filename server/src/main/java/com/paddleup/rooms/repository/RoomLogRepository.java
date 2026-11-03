package com.paddleup.rooms.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.rooms.model.LogKind;
import com.paddleup.rooms.model.RoomLogEntry;
import com.paddleup.rooms.util.Sql;
import com.paddleup.rooms.util.StoredJson;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The append-only room log: insert and select only. The database rejects anything else. */
@Repository
public class RoomLogRepository {

  private static final TypeReference<Map<String, Object>> DETAIL = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final StoredJson json;

  public RoomLogRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = new StoredJson(json);
  }

  public void append(
      UUID roomId, LogKind kind, UUID actor, Map<String, Object> detail, Instant at) {
    jdbc.sql(
            """
            INSERT INTO room_log (room_id, kind, actor_user_id, detail, occurred_at)
            VALUES (?, ?, ?, ?::jsonb, ?)
            """)
        .params(roomId, kind.value(), actor, json.write(detail), Sql.utc(at))
        .update();
  }

  public List<RoomLogEntry> byKind(UUID roomId, LogKind kind) {
    return jdbc.sql("SELECT * FROM room_log WHERE room_id = ? AND kind = ? ORDER BY id")
        .params(roomId, kind.value())
        .query(
            (rs, n) ->
                new RoomLogEntry(
                    rs.getLong("id"),
                    rs.getObject("room_id", UUID.class),
                    LogKind.fromValue(rs.getString("kind")),
                    rs.getObject("actor_user_id", UUID.class),
                    json.read(rs.getString("detail"), DETAIL),
                    Sql.instant(rs, "occurred_at")))
        .list();
  }
}
