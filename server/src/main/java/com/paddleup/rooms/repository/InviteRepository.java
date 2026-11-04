package com.paddleup.rooms.repository;

import com.paddleup.rooms.model.Invite;
import com.paddleup.rooms.util.Sql;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InviteRepository {

  private static final RowMapper<Invite> MAPPER =
      (rs, n) ->
          new Invite(
              rs.getObject("id", UUID.class),
              rs.getObject("room_id", UUID.class),
              rs.getString("code"),
              rs.getObject("issued_by", UUID.class),
              Sql.instant(rs, "issued_at"),
              Sql.instant(rs, "expires_at"),
              Sql.instant(rs, "revoked_at"));

  private final JdbcClient jdbc;

  public InviteRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(Invite invite) {
    jdbc.sql(
            """
            INSERT INTO room_invites (id, room_id, code, issued_by, issued_at, expires_at,
                                      revoked_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """)
        .params(
            invite.id(),
            invite.roomId(),
            invite.code(),
            invite.issuedBy(),
            Sql.utc(invite.issuedAt()),
            Sql.utc(invite.expiresAt()),
            Sql.utc(invite.revokedAt()))
        .update();
  }

  /** The room's invite that has not been revoked; it may still be past its expiry. */
  public Optional<Invite> findUnrevoked(UUID roomId) {
    return jdbc.sql("SELECT * FROM room_invites WHERE room_id = ? AND revoked_at IS NULL")
        .param(roomId)
        .query(MAPPER)
        .optional();
  }

  public Optional<Invite> findByCode(String code) {
    return jdbc.sql("SELECT * FROM room_invites WHERE code = ?")
        .param(code)
        .query(MAPPER)
        .optional();
  }

  public void revoke(UUID id, Instant at) {
    jdbc.sql("UPDATE room_invites SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL")
        .params(Sql.utc(at), id)
        .update();
  }
}
