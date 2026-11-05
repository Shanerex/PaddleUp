package com.paddleup.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.support.AbstractPostgresIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** AC-4, AC-5: season datasets are versioned per pack and append-only. */
class SeasonDatasetsSchemaIT extends AbstractPostgresIT {

  @Autowired JdbcTemplate jdbc;

  private UUID user() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, email, display_name, avatar, created_at, updated_at)"
            + " VALUES (?, ?, 'Ann', 'preset-01', now(), now())",
        id,
        "ds-" + id + "@example.test");
    return id;
  }

  private UUID dataset(String packId, int version) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO season_datasets (id, pack_id, version, pack_version, source_format,"
            + " imported_by, imported_at, player_count, rejected_count, set_order)"
            + " VALUES (?, ?, ?, '1.0.0', 'csv', ?, now(), 1, 0, ARRAY['Marquee'])",
        id,
        packId,
        version,
        user());
    return id;
  }

  private UUID player(UUID dataset) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO season_players (id, dataset_id, position, name, country, role_key, tag_keys,"
            + " base_price, set_name) VALUES (?, ?, 0, 'Ann', 'IN', 'r', ARRAY['t'], 20, 'Marquee')",
        id,
        dataset);
    return id;
  }

  @Test
  void aDuplicatePackAndVersionFailsButAnotherPackOrVersionDoesNot() {
    String pack = "pack-" + UUID.randomUUID();
    dataset(pack, 1);

    assertThatThrownBy(() -> dataset(pack, 1)).isInstanceOf(DuplicateKeyException.class);
    dataset(pack, 2);
    dataset("other-" + UUID.randomUUID(), 1);
  }

  @Test
  void datasetsAndPlayersRejectUpdateDeleteAndTruncate() {
    UUID d = dataset("pack-" + UUID.randomUUID(), 1);
    UUID p = player(d);

    assertThatThrownBy(
            () -> jdbc.update("UPDATE season_datasets SET player_count = 2 WHERE id = ?", d))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM season_datasets WHERE id = ?", d))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE season_datasets, season_players CASCADE"))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("UPDATE season_players SET name = 'x' WHERE id = ?", p))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM season_players WHERE id = ?", p))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE season_players CASCADE"))
        .hasMessageContaining("append-only");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM season_players WHERE dataset_id = ?", Integer.class, d))
        .isEqualTo(1);
  }
}
