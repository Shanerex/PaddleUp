package com.paddleup.scoring;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Rows scoring tests need under their own tables, inserted straight into the database. */
public final class ScoringFixtures {

  private final JdbcTemplate jdbc;

  public ScoringFixtures(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public UUID user() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, email, display_name, avatar, created_at, updated_at)"
            + " VALUES (?, ?, 'Ann', 'preset-01', now(), now())",
        id,
        "scoring-" + id + "@example.test");
    return id;
  }

  public UUID room() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO rooms (id, state, commissioner_user_id, pack_id, pack_version, preset_id,"
            + " pack_snapshot, config, created_at, updated_at)"
            + " VALUES (?, 'setup', ?, 'p', '1', 'x', '{}'::jsonb, '{}'::jsonb, now(), now())",
        id,
        user());
    return id;
  }

  public UUID dataset() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO season_datasets (id, pack_id, version, pack_version, source_format,"
            + " imported_by, imported_at, player_count, rejected_count, set_order)"
            + " VALUES (?, ?, 1, '1', 'csv', ?, now(), 1, 0, ARRAY['S'])",
        id,
        "scoring-" + id,
        user());
    return id;
  }

  public UUID seasonPlayer(UUID dataset) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO season_players (id, dataset_id, position, name, country, role_key, tag_keys,"
            + " base_price, set_name) VALUES (?, ?, 0, 'A', 'C', 'r', ARRAY[]::text[], 5, 'S')",
        id,
        dataset);
    return id;
  }
}
