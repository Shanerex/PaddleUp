package com.paddleup.auction.repository;

import com.paddleup.auction.model.Outcome;
import com.paddleup.auction.util.Sql;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Rejected intents, append-only. They are not auction events, so they live outside the event log;
 * they exist so a resent intent id gets its first answer after a restart too.
 */
@Repository
public class AuctionRejectionRepository {

  private final JdbcClient jdbc;

  public AuctionRejectionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Stores the rejection; a second rejection of the same intent id keeps the first. */
  public void append(UUID roomId, UUID actor, UUID intentId, Outcome rejection, Instant at) {
    jdbc.sql(
            """
            INSERT INTO auction_rejections (room_id, actor_user_id, intent_id, lot_no, reason,
                                            occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """)
        .params(roomId, actor, intentId, rejection.lot(), rejection.reason(), Sql.utc(at))
        .update();
  }

  /** The rejection an actor's intent id already got, if any. */
  public Optional<Outcome> find(UUID roomId, UUID actor, UUID intentId) {
    return jdbc.sql(
            "SELECT lot_no, reason FROM auction_rejections"
                + " WHERE room_id = ? AND actor_user_id = ? AND intent_id = ?")
        .params(roomId, actor, intentId)
        .query((rs, n) -> Outcome.rejected(rs.getInt("lot_no"), rs.getString("reason")))
        .optional();
  }
}
