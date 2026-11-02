package com.paddleup.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.support.AbstractPostgresIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AccountsSchemaIT extends AbstractPostgresIT {

  @Autowired JdbcTemplate jdbc;

  @Test
  void migrationCreatesAllTables() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);
    assertThat(tables)
        .contains(
            "users",
            "user_identities",
            "login_tokens",
            "spring_session",
            "spring_session_attributes");
  }

  @Test
  void accountsTimestampColumnsAreTimestamptz() {
    List<String> notTz =
        jdbc.queryForList(
            """
            SELECT table_name || '.' || column_name FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name IN ('users', 'user_identities', 'login_tokens')
              AND (column_name LIKE '%\\_at' ESCAPE '\\')
              AND data_type <> 'timestamp with time zone'
            """,
            String.class);
    assertThat(notTz).isEmpty();

    Integer tzColumns =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name IN ('users', 'user_identities', 'login_tokens')
              AND data_type = 'timestamp with time zone'
            """,
            Integer.class);
    assertThat(tzColumns).isEqualTo(6);
  }

  @Test
  void instantRoundTripsThroughUsers() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    jdbc.update(
        "INSERT INTO users (id, email, display_name, avatar, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        id,
        id + "@example.test",
        "Round Trip",
        "preset-01",
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now));

    Instant read =
        jdbc.queryForObject(
            "SELECT created_at FROM users WHERE id = ?",
            (rs, n) -> rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
            id);
    assertThat(read).isEqualTo(now);
  }
}
