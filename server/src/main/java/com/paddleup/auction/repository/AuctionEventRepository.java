package com.paddleup.auction.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.EventKind;
import com.paddleup.auction.util.PayloadJson;
import com.paddleup.auction.util.Sql;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The append-only event log. The caller assigns {@code seq}; the primary key (room_id, seq) makes a
 * second writer with the same seq fail. Nothing here updates or deletes.
 */
@Repository
public class AuctionEventRepository {

  private final JdbcClient jdbc;
  private final PayloadJson json;
  private final RowMapper<AuctionEvent> mapper;

  public AuctionEventRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = new PayloadJson(json);
    this.mapper =
        (rs, n) -> {
          int lot = rs.getInt("lot_no");
          Integer lotNo = rs.wasNull() ? null : lot;
          return new AuctionEvent(
              rs.getLong("seq"),
              Sql.instant(rs, "occurred_at"),
              EventKind.fromValue(rs.getString("kind")),
              rs.getObject("franchise_id", UUID.class),
              lotNo,
              rs.getObject("actor_user_id", UUID.class),
              rs.getObject("intent_id", UUID.class),
              this.json.read(rs.getString("payload")));
        };
  }

  public void append(UUID roomId, AuctionEvent e) {
    jdbc.sql(
            """
            INSERT INTO auction_events (room_id, seq, occurred_at, kind, franchise_id, lot_no,
                                        actor_user_id, intent_id, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """)
        .params(
            roomId,
            e.seq(),
            Sql.utc(e.at()),
            e.kind().value(),
            e.franchiseId(),
            e.lot(),
            e.actorUserId(),
            e.intentId(),
            json.write(e.payload()))
        .update();
  }

  /** Events with a seq above {@code seq}, oldest first, at most {@code limit}. */
  public List<AuctionEvent> after(UUID roomId, long seq, int limit) {
    return jdbc.sql(
            "SELECT * FROM auction_events WHERE room_id = ? AND seq > ? ORDER BY seq LIMIT ?")
        .params(roomId, seq, limit)
        .query(mapper)
        .list();
  }

  /** The whole log, oldest first, for replay on boot. */
  public List<AuctionEvent> all(UUID roomId) {
    return jdbc.sql("SELECT * FROM auction_events WHERE room_id = ? ORDER BY seq")
        .param(roomId)
        .query(mapper)
        .list();
  }

  /** The event an actor's intent id already produced, if any. */
  public Optional<AuctionEvent> byIntent(UUID roomId, UUID actor, UUID intentId) {
    return jdbc.sql(
            "SELECT * FROM auction_events WHERE room_id = ? AND actor_user_id = ?"
                + " AND intent_id = ?")
        .params(roomId, actor, intentId)
        .query(mapper)
        .optional();
  }
}
