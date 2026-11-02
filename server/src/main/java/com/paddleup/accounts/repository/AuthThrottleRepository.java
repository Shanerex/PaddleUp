package com.paddleup.accounts.repository;

import com.paddleup.accounts.util.Sql;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Counted sign-in events behind the rate limits. Call inside a transaction. */
@Repository
public class AuthThrottleRepository {

  /** Events in a window: how many, and when the oldest happened. */
  public record Usage(int count, Optional<Instant> oldest) {}

  private final JdbcClient jdbc;

  public AuthThrottleRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Serialises concurrent checks of one key until the transaction ends. */
  public void lock(String lockKey) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(?))").param(lockKey).query().singleValue();
  }

  public Usage usage(String scope, String keyHash, Instant since) {
    return jdbc.sql(
            """
            SELECT count(*) AS n, min(occurred_at) AS oldest FROM auth_throttle_events
            WHERE scope = ? AND key_hash = ? AND occurred_at > ?
            """)
        .params(scope, keyHash, Sql.utc(since))
        .query((rs, i) -> new Usage(rs.getInt("n"), Optional.ofNullable(Sql.instant(rs, "oldest"))))
        .single();
  }

  public void record(String scope, String keyHash, Instant at) {
    jdbc.sql("INSERT INTO auth_throttle_events (scope, key_hash, occurred_at) VALUES (?, ?, ?)")
        .params(scope, keyHash, Sql.utc(at))
        .update();
  }

  public void purgeBefore(Instant cutoff) {
    jdbc.sql("DELETE FROM auth_throttle_events WHERE occurred_at < ?")
        .param(Sql.utc(cutoff))
        .update();
  }
}
