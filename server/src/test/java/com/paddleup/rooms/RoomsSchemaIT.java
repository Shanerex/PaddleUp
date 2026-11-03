package com.paddleup.rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.support.AbstractPostgresIT;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class RoomsSchemaIT extends AbstractPostgresIT {

  private static final List<String> TABLES =
      List.of("rooms", "franchises", "room_invites", "room_log", "room_join_throttle_events");

  @Autowired JdbcTemplate jdbc;

  private UUID user() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, email, display_name, avatar, created_at, updated_at)"
            + " VALUES (?, ?, 'Ann', 'preset-01', now(), now())",
        id,
        "schema-" + id + "@example.test");
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

  private void franchise(UUID room, UUID owner, String name, String colour) {
    jdbc.update(
        "INSERT INTO franchises (id, room_id, owner_user_id, name, colour, monogram, created_at,"
            + " updated_at) VALUES (?, ?, ?, ?, ?, 'AB', now(), now())",
        UUID.randomUUID(),
        room,
        owner,
        name,
        colour);
  }

  @Test
  void migrationCreatesTheRoomsTables() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);
    assertThat(tables).containsAll(TABLES);
  }

  @Test
  void everyTimestampColumnIsTimestamptz() {
    List<String> notTz =
        jdbc.queryForList(
            """
            SELECT table_name || '.' || column_name FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name IN
              ('rooms', 'franchises', 'room_invites', 'room_log', 'room_join_throttle_events')
              AND (column_name LIKE '%\\_at' ESCAPE '\\')
              AND data_type <> 'timestamp with time zone'
            """,
            String.class);
    assertThat(notTz).isEmpty();
  }

  @Test
  void aRoomStateOutsideTheSixIsRejected() {
    UUID u = user();
    UUID r = room(u);
    assertThatThrownBy(() -> jdbc.update("UPDATE rooms SET state = 'bogus' WHERE id = ?", r))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void aFranchiseNameIsUniquePerRoomIgnoringCaseAndColourIsUnique() {
    UUID r = room(user());
    franchise(r, user(), "Super Kings", "crimson");

    assertThatThrownBy(() -> franchise(r, user(), "SUPER KINGS", "sky"))
        .isInstanceOf(DuplicateKeyException.class);
    assertThatThrownBy(() -> franchise(r, user(), "Other", "crimson"))
        .isInstanceOf(DuplicateKeyException.class);
    UUID other = room(user());
    franchise(other, user(), "Super Kings", "crimson");
  }

  @Test
  void anOwnerHasOneFranchisePerRoom() {
    UUID r = room(user());
    UUID owner = user();
    franchise(r, owner, "One", "crimson");

    assertThatThrownBy(() -> franchise(r, owner, "Two", "sky"))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void aRoomHasOneActiveInviteAndCodesAreUnique() {
    UUID u = user();
    UUID r = room(u);
    insertInvite(r, u, "AAAAAAAAAAAA", false);

    assertThatThrownBy(() -> insertInvite(r, u, "BBBBBBBBBBBB", false))
        .isInstanceOf(DuplicateKeyException.class);
    insertInvite(r, u, "CCCCCCCCCCCC", true);
    assertThatThrownBy(() -> insertInvite(room(u), u, "AAAAAAAAAAAA", false))
        .isInstanceOf(DuplicateKeyException.class);
  }

  private void insertInvite(UUID room, UUID by, String code, boolean revoked) {
    jdbc.update(
        "INSERT INTO room_invites (id, room_id, code, issued_by, issued_at, expires_at, revoked_at)"
            + " VALUES (?, ?, ?, ?, now(), now() + interval '7 days', ?)",
        UUID.randomUUID(),
        room,
        code,
        by,
        revoked ? java.sql.Timestamp.from(Instant.now()) : null);
  }

  @Test
  void theRoomLogRejectsUpdateDeleteAndTruncate() {
    UUID u = user();
    UUID r = room(u);
    jdbc.update(
        "INSERT INTO room_log (room_id, kind, actor_user_id, occurred_at)"
            + " VALUES (?, 'transition', ?, now())",
        r,
        u);

    assertThatThrownBy(() -> jdbc.update("UPDATE room_log SET kind = 'x' WHERE room_id = ?", r))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM room_log WHERE room_id = ?", r))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE room_log")).hasMessageContaining("append-only");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM room_log WHERE room_id = ?", Integer.class, r))
        .isEqualTo(1);
  }
}
