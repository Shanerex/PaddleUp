package com.paddleup.packs.repository;

import com.paddleup.packs.dto.DatasetFacetsDto;
import com.paddleup.packs.dto.DatasetSummaryDto;
import com.paddleup.packs.dto.SeasonDatasetDto;
import com.paddleup.packs.dto.SeasonPlayerDto;
import com.paddleup.packs.util.Sql;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Season dataset rows. Both tables are append-only: this class inserts and selects only. */
@Repository
public class SeasonDatasetRepository {

  private static final String SUMMARY_COLUMNS =
      "id, version, pack_version, source_format, imported_at, player_count, rejected_count";

  private static final RowMapper<DatasetSummaryDto> SUMMARY =
      (rs, n) ->
          new DatasetSummaryDto(
              rs.getObject("id", UUID.class),
              rs.getInt("version"),
              rs.getString("pack_version"),
              rs.getString("source_format"),
              Sql.instant(rs, "imported_at"),
              rs.getInt("player_count"),
              rs.getInt("rejected_count"));

  private static final RowMapper<SeasonPlayerDto> PLAYER =
      (rs, n) ->
          new SeasonPlayerDto(
              rs.getObject("id", UUID.class),
              rs.getInt("position"),
              rs.getString("name"),
              rs.getString("country"),
              rs.getString("role_key"),
              strings(rs.getArray("tag_keys")),
              rs.getLong("base_price"),
              rs.getString("set_name"),
              rs.getString("provider_player_id"));

  private final JdbcClient jdbc;
  private final JdbcTemplate template;

  public SeasonDatasetRepository(JdbcClient jdbc, JdbcTemplate template) {
    this.jdbc = jdbc;
    this.template = template;
  }

  private static List<String> strings(Array array) throws SQLException {
    return Arrays.asList((String[]) array.getArray());
  }

  /**
   * The next version for the pack. Holds a transaction-scoped lock on the pack id so two imports
   * cannot pick the same number; the unique constraint is the backstop.
   */
  public int nextVersion(String packId) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(?))")
        .param("season_datasets:" + packId)
        .query()
        .singleRow();
    return jdbc.sql("SELECT COALESCE(MAX(version), 0) + 1 FROM season_datasets WHERE pack_id = ?")
        .param(packId)
        .query(Integer.class)
        .single();
  }

  public void insert(
      UUID id,
      String packId,
      int version,
      String packVersion,
      String sourceFormat,
      UUID importedBy,
      Instant importedAt,
      int rejectedCount,
      List<String> setOrder,
      List<SeasonPlayerDto> players) {
    template.update(
        con -> {
          PreparedStatement ps =
              con.prepareStatement(
                  "INSERT INTO season_datasets (id, pack_id, version, pack_version, source_format,"
                      + " imported_by, imported_at, player_count, rejected_count, set_order)"
                      + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
          ps.setObject(1, id);
          ps.setString(2, packId);
          ps.setInt(3, version);
          ps.setString(4, packVersion);
          ps.setString(5, sourceFormat);
          ps.setObject(6, importedBy);
          ps.setObject(7, Sql.utc(importedAt));
          ps.setInt(8, players.size());
          ps.setInt(9, rejectedCount);
          ps.setArray(10, con.createArrayOf("text", setOrder.toArray()));
          return ps;
        });
    template.batchUpdate(
        "INSERT INTO season_players (id, dataset_id, position, name, country, role_key, tag_keys,"
            + " base_price, set_name, provider_player_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            SeasonPlayerDto p = players.get(i);
            ps.setObject(1, p.id());
            ps.setObject(2, id);
            ps.setInt(3, p.position());
            ps.setString(4, p.name());
            ps.setString(5, p.country());
            ps.setString(6, p.role());
            ps.setArray(7, ps.getConnection().createArrayOf("text", p.tags().toArray()));
            ps.setLong(8, p.basePrice());
            ps.setString(9, p.set());
            ps.setString(10, p.providerId());
          }

          @Override
          public int getBatchSize() {
            return players.size();
          }
        });
  }

  /**
   * The newest version's distinct roles, tags and base prices, read by the database so the players
   * themselves are never loaded.
   */
  public Optional<DatasetFacetsDto> latestFacets(String packId) {
    return latestSummary(packId)
        .map(
            d ->
                new DatasetFacetsDto(
                    d.version(),
                    Set.copyOf(
                        jdbc.sql("SELECT DISTINCT role_key FROM season_players WHERE dataset_id = ?")
                            .param(d.id())
                            .query(String.class)
                            .list()),
                    Set.copyOf(
                        jdbc.sql(
                                "SELECT DISTINCT unnest(tag_keys) FROM season_players"
                                    + " WHERE dataset_id = ?")
                            .param(d.id())
                            .query(String.class)
                            .list()),
                    Set.copyOf(
                        jdbc.sql(
                                "SELECT DISTINCT base_price FROM season_players"
                                    + " WHERE dataset_id = ?")
                            .param(d.id())
                            .query(Long.class)
                            .list())));
  }

  public Optional<DatasetSummaryDto> latestSummary(String packId) {
    return jdbc.sql(
            "SELECT "
                + SUMMARY_COLUMNS
                + " FROM season_datasets WHERE pack_id = ? ORDER BY version DESC LIMIT 1")
        .param(packId)
        .query(SUMMARY)
        .optional();
  }

  public List<DatasetSummaryDto> list(String packId) {
    return jdbc.sql(
            "SELECT "
                + SUMMARY_COLUMNS
                + " FROM season_datasets WHERE pack_id = ? ORDER BY version DESC")
        .param(packId)
        .query(SUMMARY)
        .list();
  }

  /** The dataset with the highest version for the pack, with its players in order. */
  public Optional<SeasonDatasetDto> latest(String packId) {
    return jdbc.sql("SELECT * FROM season_datasets WHERE pack_id = ? ORDER BY version DESC LIMIT 1")
        .param(packId)
        .query((rs, n) -> header(rs))
        .optional()
        .map(this::withPlayers);
  }

  public Optional<SeasonDatasetDto> find(UUID id) {
    return jdbc.sql("SELECT * FROM season_datasets WHERE id = ?")
        .param(id)
        .query((rs, n) -> header(rs))
        .optional()
        .map(this::withPlayers);
  }

  public Optional<SeasonPlayerDto> player(UUID datasetId, UUID playerId) {
    return jdbc.sql("SELECT * FROM season_players WHERE dataset_id = ? AND id = ?")
        .params(datasetId, playerId)
        .query(PLAYER)
        .optional();
  }

  private SeasonDatasetDto header(java.sql.ResultSet rs) throws SQLException {
    return new SeasonDatasetDto(
        rs.getObject("id", UUID.class),
        rs.getString("pack_id"),
        rs.getInt("version"),
        rs.getString("pack_version"),
        rs.getString("source_format"),
        Sql.instant(rs, "imported_at"),
        rs.getInt("player_count"),
        rs.getInt("rejected_count"),
        strings(rs.getArray("set_order")),
        List.of());
  }

  private SeasonDatasetDto withPlayers(SeasonDatasetDto header) {
    List<SeasonPlayerDto> players =
        jdbc.sql("SELECT * FROM season_players WHERE dataset_id = ? ORDER BY position")
            .param(header.id())
            .query(PLAYER)
            .list();
    return new SeasonDatasetDto(
        header.id(),
        header.packId(),
        header.version(),
        header.packVersion(),
        header.sourceFormat(),
        header.importedAt(),
        header.playerCount(),
        header.rejectedCount(),
        header.setOrder(),
        players);
  }
}
