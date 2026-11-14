package com.paddleup.rooms.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.packs.dto.AppearanceRuleDto;
import com.paddleup.rooms.dto.PackLabelsDto;
import com.paddleup.rooms.dto.RoomConfigDto;
import com.paddleup.rooms.dto.RoomState;
import com.paddleup.rooms.model.Room;
import com.paddleup.rooms.util.Sql;
import com.paddleup.rooms.util.StoredJson;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Room rows. Config and the pack snapshot are JSONB, read without request limits. A config too
 * large to store is refused with an invalid-config error before anything is written.
 */
@Repository
public class RoomRepository {

  private final JdbcClient jdbc;
  private final StoredJson json;
  private final RowMapper<Room> mapper;

  public RoomRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = new StoredJson(json);
    this.mapper = this::map;
  }

  private Room map(java.sql.ResultSet rs, int row) throws SQLException {
    return new Room(
        rs.getObject("id", UUID.class),
        RoomState.fromValue(rs.getString("state")),
        rs.getObject("commissioner_user_id", UUID.class),
        rs.getString("pack_id"),
        rs.getString("pack_version"),
        rs.getString("preset_id"),
        json.read(rs.getString("pack_snapshot"), PackLabelsDto.class),
        json.read(rs.getString("config"), RoomConfigDto.class),
        rs.getInt("config_version"),
        Sql.instant(rs, "created_at"),
        Sql.instant(rs, "updated_at"));
  }

  public void insert(Room room) {
    jdbc.sql(
            """
            INSERT INTO rooms (id, state, commissioner_user_id, pack_id, pack_version, preset_id,
                               pack_snapshot, config, config_version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
            """)
        .params(
            room.id(),
            room.state().value(),
            room.commissionerUserId(),
            room.packId(),
            room.packVersion(),
            room.presetId(),
            json.write(room.packSnapshot()),
            json.writeConfig(room.config()),
            room.configVersion(),
            Sql.utc(room.createdAt()),
            Sql.utc(room.updatedAt()))
        .update();
  }

  /** Rooms the user commissions or owns a franchise in, most recently changed first. */
  public List<Room> findForMember(UUID userId, int limit) {
    return jdbc.sql(
            "SELECT * FROM rooms WHERE commissioner_user_id = ? OR id IN"
                + " (SELECT room_id FROM franchises WHERE owner_user_id = ?)"
                + " ORDER BY updated_at DESC, id LIMIT ?")
        .param(userId)
        .param(userId)
        .param(limit)
        .query(mapper)
        .list();
  }

  public Optional<Room> find(UUID id) {
    return jdbc.sql("SELECT * FROM rooms WHERE id = ?").param(id).query(mapper).optional();
  }

  /** Reads the room and holds its row lock until the transaction ends. */
  public Optional<Room> findForUpdate(UUID id) {
    return jdbc.sql("SELECT * FROM rooms WHERE id = ? FOR UPDATE")
        .param(id)
        .query(mapper)
        .optional();
  }

  /** Ids and pack ids of the rooms whose pack snapshot has no appearance rule. */
  public List<Map.Entry<UUID, String>> withoutAppearanceRule() {
    return jdbc.sql(
            "SELECT id, pack_id FROM rooms WHERE pack_snapshot ->> 'appearanceRule' IS NULL")
        .query((rs, n) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("pack_id")))
        .list();
  }

  /** Writes the appearance rule into the room's snapshot, only if it still has none. */
  public boolean setAppearanceRule(UUID id, AppearanceRuleDto rule) {
    return jdbc.sql(
                """
                UPDATE rooms SET pack_snapshot = jsonb_set(pack_snapshot, '{appearanceRule}',
                                                           ?::jsonb)
                WHERE id = ? AND pack_snapshot ->> 'appearanceRule' IS NULL
                """)
            .params(json.write(rule), id)
            .update()
        == 1;
  }

  /** Saves the config and bumps its version, only if the stored version is still {@code from}. */
  public boolean updateConfig(UUID id, RoomConfigDto config, int from, Instant now) {
    return jdbc.sql(
                """
                UPDATE rooms SET config = ?::jsonb, config_version = config_version + 1,
                                 updated_at = ?
                WHERE id = ? AND config_version = ?
                """)
            .params(json.writeConfig(config), Sql.utc(now), id, from)
            .update()
        == 1;
  }

  public void updateState(UUID id, RoomState state, Instant now) {
    jdbc.sql("UPDATE rooms SET state = ?, updated_at = ? WHERE id = ?")
        .params(state.value(), Sql.utc(now), id)
        .update();
  }

  public void updateCommissioner(UUID id, UUID userId, Instant now) {
    jdbc.sql("UPDATE rooms SET commissioner_user_id = ?, updated_at = ? WHERE id = ?")
        .params(userId, Sql.utc(now), id)
        .update();
  }
}
