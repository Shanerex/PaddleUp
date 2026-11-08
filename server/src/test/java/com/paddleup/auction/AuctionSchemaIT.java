package com.paddleup.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.support.AbstractPostgresIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** AC-11: the event log and lot cards are append-only and the seq is unique per room. */
class AuctionSchemaIT extends AbstractPostgresIT {

  @Autowired JdbcTemplate jdbc;

  private UUID user() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, email, display_name, avatar, created_at, updated_at)"
            + " VALUES (?, ?, 'Ann', 'preset-01', now(), now())",
        id,
        "auction-" + id + "@example.test");
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

  private void event(UUID room, long seq, UUID actor, UUID intent) {
    jdbc.update(
        "INSERT INTO auction_events (room_id, seq, occurred_at, kind, actor_user_id, intent_id)"
            + " VALUES (?, ?, now(), 'bid_accepted', ?, ?)",
        room,
        seq,
        actor,
        intent);
  }

  @Test
  void updateDeleteAndTruncateOfTheEventLogAreRejected() {
    UUID room = room();
    event(room, 1, null, null);

    assertThatThrownBy(
            () -> jdbc.update("UPDATE auction_events SET kind = 'x' WHERE room_id = ?", room))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM auction_events WHERE room_id = ?", room))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE auction_events"))
        .hasMessageContaining("append-only");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auction_events WHERE room_id = ?", Integer.class, room))
        .isEqualTo(1);
  }

  @Test
  void lotCardsAreAppendOnlyToo() {
    UUID room = room();
    jdbc.update(
        "INSERT INTO auction_lots (room_id, lot_no, pool_player_id, set_name, position_in_set,"
            + " name, country, role_key, tag_keys, base_price)"
            + " VALUES (?, 1, ?, 'S', 0, 'A', 'C', 'r', ARRAY[]::text[], 5)",
        room,
        UUID.randomUUID());

    assertThatThrownBy(
            () -> jdbc.update("UPDATE auction_lots SET name = 'B' WHERE room_id = ?", room))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM auction_lots WHERE room_id = ?", room))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE auction_lots"))
        .hasMessageContaining("append-only");
  }

  @Test
  void aDuplicateSeqInARoomFailsButAnotherRoomMayReuseIt() {
    UUID room = room();
    event(room, 1, null, null);

    assertThatThrownBy(() -> event(room, 1, null, null)).isInstanceOf(DuplicateKeyException.class);
    event(room(), 1, null, null);
  }

  @Test
  void anIntentIdIsUniquePerRoomAndActor() {
    UUID room = room();
    UUID actor = user();
    UUID intent = UUID.randomUUID();
    event(room, 1, actor, intent);

    assertThatThrownBy(() -> event(room, 2, actor, intent))
        .isInstanceOf(DuplicateKeyException.class);
    event(room, 2, user(), intent);
    event(room, 3, null, null);
    event(room, 4, null, null);
  }

  @Test
  void anAuctionPhaseOutsideTheThreeIsRejected() {
    UUID room = room();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO auctions (room_id, phase, starts_at, scheduled_by, created_at,"
                        + " updated_at) VALUES (?, 'bogus', now(), ?, now(), now())",
                    room,
                    user()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
}
