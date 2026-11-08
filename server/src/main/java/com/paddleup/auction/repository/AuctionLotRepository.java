package com.paddleup.auction.repository;

import com.paddleup.auction.model.Lot;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lot cards frozen at go-live. Append-only, like the log. */
@Repository
public class AuctionLotRepository {

  private static final RowMapper<Lot> MAPPER =
      (rs, n) ->
          new Lot(
              rs.getInt("lot_no"),
              rs.getObject("pool_player_id", UUID.class),
              rs.getString("name"),
              rs.getString("country"),
              rs.getString("role_key"),
              strings(rs.getArray("tag_keys")),
              rs.getString("set_name"),
              rs.getInt("position_in_set"),
              rs.getLong("base_price"));

  private final JdbcClient jdbc;
  private final JdbcTemplate template;

  public AuctionLotRepository(JdbcClient jdbc, JdbcTemplate template) {
    this.jdbc = jdbc;
    this.template = template;
  }

  private static List<String> strings(Array array) throws SQLException {
    return Arrays.asList((String[]) array.getArray());
  }

  public void insertAll(UUID roomId, List<Lot> lots) {
    template.batchUpdate(
        "INSERT INTO auction_lots (room_id, lot_no, pool_player_id, set_name, position_in_set,"
            + " name, country, role_key, tag_keys, base_price) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            Lot l = lots.get(i);
            ps.setObject(1, roomId);
            ps.setInt(2, l.no());
            ps.setObject(3, l.playerId());
            ps.setString(4, l.set());
            ps.setInt(5, l.position());
            ps.setString(6, l.name());
            ps.setString(7, l.country());
            ps.setString(8, l.role());
            ps.setArray(9, ps.getConnection().createArrayOf("text", l.tags().toArray()));
            ps.setLong(10, l.basePrice());
          }

          @Override
          public int getBatchSize() {
            return lots.size();
          }
        });
  }

  public List<Lot> byRoom(UUID roomId) {
    return jdbc.sql("SELECT * FROM auction_lots WHERE room_id = ? ORDER BY lot_no")
        .param(roomId)
        .query(MAPPER)
        .list();
  }
}
