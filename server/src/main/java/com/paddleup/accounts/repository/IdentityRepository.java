package com.paddleup.accounts.repository;

import com.paddleup.accounts.model.Identity;
import com.paddleup.accounts.util.Sql;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {

  private final JdbcClient jdbc;

  public IdentityRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<UUID> findUserId(String provider, String subject) {
    return jdbc.sql("SELECT user_id FROM user_identities WHERE provider = ? AND subject = ?")
        .params(provider, subject)
        .query(UUID.class)
        .optional();
  }

  public void insert(Identity identity) {
    jdbc.sql(
            "INSERT INTO user_identities (user_id, provider, subject, created_at)"
                + " VALUES (?, ?, ?, ?)")
        .params(
            identity.userId(),
            identity.provider(),
            identity.subject(),
            Sql.utc(identity.createdAt()))
        .update();
  }
}
