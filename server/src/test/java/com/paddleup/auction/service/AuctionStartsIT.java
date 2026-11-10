package com.paddleup.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.auction.model.AuctionHeader;
import com.paddleup.auction.model.Phase;
import com.paddleup.auction.repository.AuctionRepository;
import com.paddleup.rooms.service.AuctionStartSource;
import com.paddleup.support.AbstractPostgresIT;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** AC-10: the batch start lookup holds only rooms that have an auction start. */
class AuctionStartsIT extends AbstractPostgresIT {

  static final Instant AT = Instant.parse("2030-01-01T10:00:00Z");

  @Autowired JdbcTemplate jdbc;
  @Autowired AuctionRepository headers;
  @Autowired AuctionStartSource source;

  private UUID commissioner;

  private UUID room() {
    UUID user = UUID.randomUUID();
    commissioner = user;
    jdbc.update(
        "INSERT INTO users (id, email, display_name, avatar, created_at, updated_at)"
            + " VALUES (?, ?, 'Ann', 'preset-01', now(), now())",
        user,
        "starts-" + user + "@example.test");
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO rooms (id, state, commissioner_user_id, pack_id, pack_version, preset_id,"
            + " pack_snapshot, config, created_at, updated_at)"
            + " VALUES (?, 'setup', ?, 'p', '1', 'x', '{}'::jsonb, '{}'::jsonb, now(), now())",
        id,
        user);
    return id;
  }

  @Test
  void theMapHoldsOnlyRoomsWithAStart() {
    UUID scheduled = room();
    UUID other = room();
    UUID none = room();
    headers.upsert(new AuctionHeader(scheduled, Phase.SCHEDULED, AT, commissioner, null, 1), 0, AT);
    headers.upsert(
        new AuctionHeader(other, Phase.SCHEDULED, AT.plusSeconds(60), commissioner, null, 1),
        0,
        AT);

    var starts = source.startsAt(List.of(scheduled, none));

    assertThat(starts).containsOnlyKeys(scheduled);
    assertThat(starts.get(scheduled)).isEqualTo(AT);
  }

  @Test
  void anEmptyRequestIsAnEmptyMap() {
    assertThat(source.startsAt(List.of())).isEmpty();
  }
}
