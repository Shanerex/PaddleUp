package com.paddleup.accounts.repository;

import com.paddleup.accounts.model.User;
import com.paddleup.accounts.util.Sql;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

  private static final RowMapper<User> MAPPER =
      (rs, n) ->
          new User(
              rs.getObject("id", UUID.class),
              rs.getString("email"),
              rs.getString("display_name"),
              rs.getString("avatar"),
              rs.getString("google_picture_url"),
              Sql.instant(rs, "created_at"),
              Sql.instant(rs, "updated_at"));

  private final JdbcClient jdbc;

  public UserRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public User insert(User user) {
    jdbc.sql(
            """
            INSERT INTO users (id, email, display_name, avatar, google_picture_url,
                               created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """)
        .params(
            user.id(),
            user.email(),
            user.displayName(),
            user.avatar(),
            user.googlePictureUrl(),
            Sql.utc(user.createdAt()),
            Sql.utc(user.updatedAt()))
        .update();
    return user;
  }

  public Optional<User> findById(UUID id) {
    return jdbc.sql("SELECT * FROM users WHERE id = ?").param(id).query(MAPPER).optional();
  }

  public Optional<User> findByEmail(String email) {
    return jdbc.sql("SELECT * FROM users WHERE email = ?").param(email).query(MAPPER).optional();
  }

  public void updateDisplayName(UUID id, String displayName, Instant now) {
    update("display_name", id, displayName, now);
  }

  public void updateAvatar(UUID id, String avatar, Instant now) {
    update("avatar", id, avatar, now);
  }

  public void updateGooglePictureUrl(UUID id, String url, Instant now) {
    update("google_picture_url", id, url, now);
  }

  private void update(String column, UUID id, String value, Instant now) {
    jdbc.sql("UPDATE users SET " + column + " = ?, updated_at = ? WHERE id = ?")
        .params(value, Sql.utc(now), id)
        .update();
  }
}
