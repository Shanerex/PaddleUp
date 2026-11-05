package com.paddleup.rooms.repository;

import com.paddleup.rooms.dto.PoolOrigin;
import com.paddleup.rooms.model.PoolPlayer;
import com.paddleup.rooms.model.RoomPool;
import com.paddleup.rooms.util.Sql;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** A room's pool copy: one header row and its players. Callers hold the room row lock to write. */
@Repository
public class RoomPoolRepository {

  private static final RowMapper<RoomPool> HEADER =
      (rs, n) ->
          new RoomPool(
              rs.getObject("room_id", UUID.class),
              rs.getObject("dataset_id", UUID.class),
              rs.getInt("dataset_version"),
              rs.getInt("source_count"),
              rs.getInt("pool_version"),
              strings(rs.getArray("set_order")),
              Sql.instant(rs, "created_at"),
              Sql.instant(rs, "updated_at"));

  private static final RowMapper<PoolPlayer> PLAYER =
      (rs, n) ->
          new PoolPlayer(
              rs.getObject("id", UUID.class),
              rs.getObject("source_player_id", UUID.class),
              PoolOrigin.fromValue(rs.getString("origin")),
              rs.getString("name"),
              rs.getString("country"),
              rs.getString("role_key"),
              strings(rs.getArray("tag_keys")),
              rs.getLong("base_price"),
              rs.getString("set_name"),
              rs.getString("provider_player_id"),
              rs.getInt("position"));

  private static final String INSERT_PLAYER =
      "INSERT INTO room_pool_players (id, room_id, source_player_id, origin, name, country,"
          + " role_key, tag_keys, base_price, set_name, provider_player_id, position)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private final JdbcClient jdbc;
  private final JdbcTemplate template;

  public RoomPoolRepository(JdbcClient jdbc, JdbcTemplate template) {
    this.jdbc = jdbc;
    this.template = template;
  }

  private static List<String> strings(Array array) throws SQLException {
    return Arrays.asList((String[]) array.getArray());
  }

  public boolean exists(UUID roomId) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM room_pools WHERE room_id = ?)")
        .param(roomId)
        .query(Boolean.class)
        .single();
  }

  public Optional<RoomPool> find(UUID roomId) {
    return jdbc.sql("SELECT * FROM room_pools WHERE room_id = ?")
        .param(roomId)
        .query(HEADER)
        .optional();
  }

  public List<PoolPlayer> players(UUID roomId) {
    return jdbc.sql("SELECT * FROM room_pool_players WHERE room_id = ? ORDER BY position, id")
        .param(roomId)
        .query(PLAYER)
        .list();
  }

  public Optional<PoolPlayer> player(UUID roomId, UUID playerId) {
    return jdbc.sql("SELECT * FROM room_pool_players WHERE room_id = ? AND id = ?")
        .params(roomId, playerId)
        .query(PLAYER)
        .optional();
  }

  public int nextPosition(UUID roomId) {
    return jdbc.sql(
            "SELECT COALESCE(MAX(position), -1) + 1 FROM room_pool_players WHERE room_id = ?")
        .param(roomId)
        .query(Integer.class)
        .single();
  }

  public void insertHeader(RoomPool pool) {
    template.update(
        con -> {
          PreparedStatement ps =
              con.prepareStatement(
                  "INSERT INTO room_pools (room_id, dataset_id, dataset_version, source_count,"
                      + " pool_version, set_order, created_at, updated_at)"
                      + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
          ps.setObject(1, pool.roomId());
          ps.setObject(2, pool.datasetId());
          ps.setInt(3, pool.datasetVersion());
          ps.setInt(4, pool.sourceCount());
          ps.setInt(5, pool.poolVersion());
          ps.setArray(6, con.createArrayOf("text", pool.setOrder().toArray()));
          ps.setObject(7, Sql.utc(pool.createdAt()));
          ps.setObject(8, Sql.utc(pool.updatedAt()));
          return ps;
        });
  }

  /**
   * Points the header at another source and set order and bumps its version; true if it was {@code
   * from}.
   */
  public boolean replaceSource(
      UUID roomId,
      UUID datasetId,
      int datasetVersion,
      int sourceCount,
      List<String> setOrder,
      int from,
      Instant now) {
    return template.update(
            con -> {
              PreparedStatement ps =
                  con.prepareStatement(
                      "UPDATE room_pools SET dataset_id = ?, dataset_version = ?, source_count = ?,"
                          + " set_order = ?, pool_version = pool_version + 1, updated_at = ?"
                          + " WHERE room_id = ? AND pool_version = ?");
              ps.setObject(1, datasetId);
              ps.setInt(2, datasetVersion);
              ps.setInt(3, sourceCount);
              ps.setArray(4, con.createArrayOf("text", setOrder.toArray()));
              ps.setObject(5, Sql.utc(now));
              ps.setObject(6, roomId);
              ps.setInt(7, from);
              return ps;
            })
        == 1;
  }

  /** Bumps the version and, when {@code setOrder} is given, replaces the order. */
  public boolean bump(UUID roomId, List<String> setOrder, int from, Instant now) {
    return template.update(
            con -> {
              PreparedStatement ps =
                  con.prepareStatement(
                      "UPDATE room_pools SET set_order = COALESCE(?, set_order),"
                          + " pool_version = pool_version + 1, updated_at = ?"
                          + " WHERE room_id = ? AND pool_version = ?");
              ps.setArray(
                  1, setOrder == null ? null : con.createArrayOf("text", setOrder.toArray()));
              ps.setObject(2, Sql.utc(now));
              ps.setObject(3, roomId);
              ps.setInt(4, from);
              return ps;
            })
        == 1;
  }

  public void insertPlayers(UUID roomId, List<PoolPlayer> players) {
    template.batchUpdate(
        INSERT_PLAYER,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            PoolPlayer p = players.get(i);
            ps.setObject(1, p.id());
            ps.setObject(2, roomId);
            ps.setObject(3, p.sourcePlayerId());
            ps.setString(4, p.origin().value());
            ps.setString(5, p.name());
            ps.setString(6, p.country());
            ps.setString(7, p.role());
            ps.setArray(8, ps.getConnection().createArrayOf("text", p.tags().toArray()));
            ps.setLong(9, p.basePrice());
            ps.setString(10, p.set());
            ps.setString(11, p.providerId());
            ps.setInt(12, p.position());
          }

          @Override
          public int getBatchSize() {
            return players.size();
          }
        });
  }

  public void updatePlayer(UUID roomId, PoolPlayer p) {
    template.update(
        con -> {
          PreparedStatement ps =
              con.prepareStatement(
                  "UPDATE room_pool_players SET origin = ?, name = ?, country = ?, role_key = ?,"
                      + " tag_keys = ?, base_price = ?, set_name = ?, provider_player_id = ?"
                      + " WHERE room_id = ? AND id = ?");
          ps.setString(1, p.origin().value());
          ps.setString(2, p.name());
          ps.setString(3, p.country());
          ps.setString(4, p.role());
          ps.setArray(5, con.createArrayOf("text", p.tags().toArray()));
          ps.setLong(6, p.basePrice());
          ps.setString(7, p.set());
          ps.setString(8, p.providerId());
          ps.setObject(9, roomId);
          ps.setObject(10, p.id());
          return ps;
        });
  }

  public void deletePlayer(UUID roomId, UUID playerId) {
    jdbc.sql("DELETE FROM room_pool_players WHERE room_id = ? AND id = ?")
        .params(roomId, playerId)
        .update();
  }

  public void deletePlayers(UUID roomId) {
    jdbc.sql("DELETE FROM room_pool_players WHERE room_id = ?").param(roomId).update();
  }
}
