package com.paddleup.rooms.repository;

import com.paddleup.rooms.model.Franchise;
import com.paddleup.rooms.util.Sql;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FranchiseRepository {

  private static final RowMapper<Franchise> MAPPER =
      (rs, n) ->
          new Franchise(
              rs.getObject("id", UUID.class),
              rs.getObject("room_id", UUID.class),
              rs.getObject("owner_user_id", UUID.class),
              rs.getString("name"),
              rs.getString("colour"),
              rs.getString("monogram"),
              Sql.instant(rs, "created_at"),
              Sql.instant(rs, "updated_at"));

  private final JdbcClient jdbc;

  public FranchiseRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(Franchise f) {
    jdbc.sql(
            """
            INSERT INTO franchises (id, room_id, owner_user_id, name, colour, monogram,
                                    created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .params(
            f.id(),
            f.roomId(),
            f.ownerUserId(),
            f.name(),
            f.colour(),
            f.monogram(),
            Sql.utc(f.createdAt()),
            Sql.utc(f.updatedAt()))
        .update();
  }

  public Optional<Franchise> findById(UUID id) {
    return jdbc.sql("SELECT * FROM franchises WHERE id = ?").param(id).query(MAPPER).optional();
  }

  public Optional<Franchise> findByOwner(UUID roomId, UUID userId) {
    return jdbc.sql("SELECT * FROM franchises WHERE room_id = ? AND owner_user_id = ?")
        .params(roomId, userId)
        .query(MAPPER)
        .optional();
  }

  public List<Franchise> findByRoom(UUID roomId) {
    return jdbc.sql("SELECT * FROM franchises WHERE room_id = ? ORDER BY created_at, id")
        .param(roomId)
        .query(MAPPER)
        .list();
  }

  public int count(UUID roomId) {
    return jdbc.sql("SELECT count(*) FROM franchises WHERE room_id = ?")
        .param(roomId)
        .query(Integer.class)
        .single();
  }

  public void update(UUID id, String name, String colour, String monogram, Instant now) {
    jdbc.sql(
            "UPDATE franchises SET name = ?, colour = ?, monogram = ?, updated_at = ?"
                + " WHERE id = ?")
        .params(name, colour, monogram, Sql.utc(now), id)
        .update();
  }

  public void delete(UUID id) {
    jdbc.sql("DELETE FROM franchises WHERE id = ?").param(id).update();
  }
}
