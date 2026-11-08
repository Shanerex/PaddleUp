package com.paddleup.rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.support.AbstractPostgresIT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** R-5, R-8: the room pool header is unique per room and its players are well formed. */
class RoomPoolsSchemaIT extends AbstractPostgresIT {

  @Autowired JdbcTemplate jdbc;

  private UUID user() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, email, display_name, avatar, created_at, updated_at)"
            + " VALUES (?, ?, 'Ann', 'preset-01', now(), now())",
        id,
        "pool-" + id + "@example.test");
    return id;
  }

  private UUID room() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO rooms (id, state, commissioner_user_id, pack_id, pack_version, preset_id,"
            + " pack_snapshot, config, created_at, updated_at)"
            + " VALUES (?, 'setup', ?, 'p', '1', 'x', '{}'::jsonb, '{}'::jsonb, now(), now())",
        id,
        user());
    return id;
  }

  private UUID dataset() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO season_datasets (id, pack_id, version, pack_version, source_format,"
            + " imported_by, imported_at, player_count, rejected_count, set_order)"
            + " VALUES (?, ?, 1, '1', 'csv', ?, now(), 1, 0, ARRAY['S'])",
        id,
        "schema-" + id,
        user());
    return id;
  }

  private UUID sourcePlayer(UUID dataset) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO season_players (id, dataset_id, position, name, country, role_key, tag_keys,"
            + " base_price, set_name) VALUES (?, ?, 0, 'A', 'C', 'r', ARRAY[]::text[], 5, 'S')",
        id,
        dataset);
    return id;
  }

  private void pool(UUID room, UUID dataset) {
    jdbc.update(
        "INSERT INTO room_pools (room_id, dataset_id, dataset_version, source_count, set_order,"
            + " created_at, updated_at) VALUES (?, ?, 1, 1, ARRAY['S'], now(), now())",
        room,
        dataset);
  }

  private void player(UUID room, String origin, UUID source) {
    jdbc.update(
        "INSERT INTO room_pool_players (id, room_id, source_player_id, origin, name, country,"
            + " role_key, tag_keys, base_price, set_name, position)"
            + " VALUES (?, ?, ?, ?, 'A', 'C', 'r', ARRAY[]::text[], 5, 'S', 0)",
        UUID.randomUUID(),
        room,
        source,
        origin);
  }

  @Test
  void theMigrationCreatesTheTables() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);
    assertThat(tables).contains("room_pools", "room_pool_players");
  }

  @Test
  void aRoomHasOneHeaderAndItsForeignKeysAreEnforced() {
    UUID room = room();
    UUID ds = dataset();
    pool(room, ds);

    assertThatThrownBy(() -> pool(room, ds)).isInstanceOf(DuplicateKeyException.class);
    assertThatThrownBy(() -> pool(UUID.randomUUID(), ds))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> pool(room(), UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> player(UUID.randomUUID(), "added", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void theOriginMustBeOneOfThreeAndMatchTheSource() {
    UUID room = room();
    UUID ds = dataset();
    UUID src = sourcePlayer(ds);
    pool(room, ds);

    player(room, "source", src);
    player(room, "edited", src);
    player(room, "added", null);
    assertThatThrownBy(() -> player(room, "bogus", src)).isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> player(room, "added", src)).isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> player(room, "source", null)).isInstanceOf(DataAccessException.class);
  }

  @Test
  void aPriceOfZeroIsRejected() {
    UUID room = room();
    pool(room, dataset());

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO room_pool_players (id, room_id, origin, name, country, role_key,"
                        + " tag_keys, base_price, set_name, position)"
                        + " VALUES (?, ?, 'added', 'A', 'C', 'r', ARRAY[]::text[], 0, 'S', 0)",
                    UUID.randomUUID(),
                    room))
        .isInstanceOf(DataAccessException.class);
  }
}
