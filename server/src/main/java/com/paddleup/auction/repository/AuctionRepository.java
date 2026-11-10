package com.paddleup.auction.repository;

import com.paddleup.auction.model.AuctionHeader;
import com.paddleup.auction.model.Phase;
import com.paddleup.auction.util.Sql;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The {@code auctions} header rows: an index for boot discovery. */
@Repository
public class AuctionRepository {

  private static final RowMapper<AuctionHeader> MAPPER =
      (rs, n) ->
          new AuctionHeader(
              rs.getObject("room_id", UUID.class),
              Phase.fromValue(rs.getString("phase")),
              Sql.instant(rs, "starts_at"),
              rs.getObject("scheduled_by", UUID.class),
              Sql.instant(rs, "start_refused_at"),
              rs.getLong("last_seq"));

  private final JdbcClient jdbc;

  public AuctionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<AuctionHeader> find(UUID roomId) {
    return jdbc.sql("SELECT * FROM auctions WHERE room_id = ?")
        .param(roomId)
        .query(MAPPER)
        .optional();
  }

  /** The start time of each given room that has an auction row; other rooms are absent. */
  public Map<UUID, Instant> startsAt(Collection<UUID> roomIds) {
    Map<UUID, Instant> starts = new HashMap<>();
    if (roomIds.isEmpty()) {
      return starts;
    }
    jdbc.sql("SELECT room_id, starts_at FROM auctions WHERE room_id = ANY(?)")
        .param(roomIds.toArray(UUID[]::new))
        .query(
            rs -> {
              starts.put(rs.getObject("room_id", UUID.class), Sql.instant(rs, "starts_at"));
            });
    return starts;
  }

  /** Rooms whose auction has not ended, for boot recovery. */
  public List<UUID> unended() {
    return jdbc.sql("SELECT room_id FROM auctions WHERE phase <> 'ended' ORDER BY created_at")
        .query(UUID.class)
        .list();
  }

  /**
   * Writes the header as it stands after events up to {@code header.lastSeq()}. A new row is
   * inserted; an existing one is changed only if its last seq is still {@code fromSeq}, so a second
   * writer cannot move it. Returns false when it was not.
   */
  public boolean upsert(AuctionHeader header, long fromSeq, Instant now) {
    return jdbc.sql(
                """
                INSERT INTO auctions (room_id, phase, starts_at, scheduled_by, start_refused_at,
                                      last_seq, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (room_id) DO UPDATE SET
                    phase = EXCLUDED.phase,
                    starts_at = EXCLUDED.starts_at,
                    scheduled_by = EXCLUDED.scheduled_by,
                    start_refused_at = EXCLUDED.start_refused_at,
                    last_seq = EXCLUDED.last_seq,
                    updated_at = EXCLUDED.updated_at
                WHERE auctions.last_seq = ?
                """)
            .params(
                header.roomId(),
                header.phase().value(),
                Sql.utc(header.startsAt()),
                header.scheduledBy(),
                Sql.utc(header.startRefusedAt()),
                header.lastSeq(),
                Sql.utc(now),
                Sql.utc(now),
                fromSeq)
            .update()
        == 1;
  }
}
