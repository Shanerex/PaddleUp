package com.paddleup.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.support.AbstractPostgresIT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** AC-15, AC-17: the scoring tables exist and the rule change log is append-only. */
class ScoringSchemaIT extends AbstractPostgresIT {

  @Autowired JdbcTemplate jdbc;

  private UUID user() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, email, display_name, avatar, created_at, updated_at)"
            + " VALUES (?, ?, 'Ann', 'preset-01', now(), now())",
        id,
        "scoring-" + id + "@example.test");
    return id;
  }

  private UUID room(UUID commissioner) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO rooms (id, state, commissioner_user_id, pack_id, pack_version, preset_id,"
            + " pack_snapshot, config, created_at, updated_at)"
            + " VALUES (?, 'setup', ?, 'p', '1', 'x', '{}'::jsonb, '{}'::jsonb, now(), now())",
        id,
        commissioner);
    return id;
  }

  private long logEntry(UUID room, UUID actor, String kind) {
    return jdbc.queryForObject(
        "INSERT INTO scoring_rule_log (room_id, kind, actor, occurred_at, before, after, diff)"
            + " VALUES (?, ?, ?, now(), '{}'::jsonb, '{}'::jsonb, '{}'::jsonb) RETURNING seq",
        Long.class,
        room,
        kind,
        actor);
  }

  @Test
  void theMigrationCreatesTheSixTables() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);
    assertThat(tables)
        .contains(
            "scoring_rule_sets",
            "scoring_rule_log",
            "scoring_matches",
            "scoring_stat_values",
            "scoring_stat_overrides",
            "scoring_points");
  }

  @Test
  void theRuleLogRejectsUpdateDeleteAndTruncate() {
    UUID actor = user();
    UUID room = room(actor);
    long seq = logEntry(room, actor, "edit");

    assertThatThrownBy(
            () -> jdbc.update("UPDATE scoring_rule_log SET reason = 'x' WHERE seq = ?", seq))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM scoring_rule_log WHERE seq = ?", seq))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE scoring_rule_log"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM scoring_rule_log WHERE seq = ?", Integer.class, seq))
        .isEqualTo(1);
  }

  @Test
  void theLogKindMustBeOneOfThree() {
    UUID actor = user();
    UUID room = room(actor);

    logEntry(room, actor, "revert");
    logEntry(room, actor, "post_freeze_change");
    assertThatThrownBy(() -> logEntry(room, actor, "bogus"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void aRuleSetRowIsOnePerRoomAndStartsAtVersionZero() {
    UUID room = room(user());
    jdbc.update("INSERT INTO scoring_rule_sets (room_id, updated_at) VALUES (?, now())", room);

    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM scoring_rule_sets WHERE room_id = ?", Integer.class, room))
        .isZero();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO scoring_rule_sets (room_id, updated_at) VALUES (?, now())", room))
        .isInstanceOf(DataAccessException.class);
  }
}
