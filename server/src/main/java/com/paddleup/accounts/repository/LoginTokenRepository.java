package com.paddleup.accounts.repository;

import com.paddleup.accounts.model.LoginToken;
import com.paddleup.accounts.util.Sql;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LoginTokenRepository {

  private static final RowMapper<LoginToken> MAPPER =
      (rs, n) ->
          new LoginToken(
              rs.getObject("id", UUID.class),
              rs.getString("email"),
              rs.getString("link_hash"),
              rs.getString("code_hash"),
              Sql.instant(rs, "expires_at"),
              Sql.instant(rs, "used_at"),
              rs.getInt("attempts"),
              Sql.instant(rs, "created_at"));

  private final JdbcClient jdbc;

  public LoginTokenRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(LoginToken token) {
    jdbc.sql(
            """
            INSERT INTO login_tokens (id, email, link_hash, code_hash, expires_at, used_at,
                                      attempts, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .params(
            token.id(),
            token.email(),
            token.linkHash(),
            token.codeHash(),
            Sql.utc(token.expiresAt()),
            Sql.utc(token.usedAt()),
            token.attempts(),
            Sql.utc(token.createdAt()))
        .update();
  }

  /** Atomically spends an open token by its link hash and returns its email. */
  public Optional<String> consumeByLinkHash(String linkHash, Instant now) {
    return jdbc.sql(
            """
            UPDATE login_tokens SET used_at = ?
            WHERE link_hash = ? AND used_at IS NULL AND expires_at > ?
            RETURNING email
            """)
        .params(Sql.utc(now), linkHash, Sql.utc(now))
        .query(String.class)
        .optional();
  }

  /** Newest unused, unexpired token for the email, row-locked; call inside a transaction. */
  public Optional<LoginToken> findLatestOpenForUpdate(String email, Instant now) {
    return jdbc.sql(
            """
            SELECT * FROM login_tokens
            WHERE email = ? AND used_at IS NULL AND expires_at > ?
            ORDER BY created_at DESC LIMIT 1
            FOR UPDATE
            """)
        .params(email, Sql.utc(now))
        .query(MAPPER)
        .optional();
  }

  public void incrementAttempts(UUID id) {
    jdbc.sql("UPDATE login_tokens SET attempts = attempts + 1 WHERE id = ?").param(id).update();
  }

  public void markUsed(UUID id, Instant now) {
    jdbc.sql("UPDATE login_tokens SET used_at = ? WHERE id = ?").params(Sql.utc(now), id).update();
  }
}
