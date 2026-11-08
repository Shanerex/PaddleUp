package com.paddleup.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionHeader;
import com.paddleup.auction.model.Events;
import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.Phase;
import com.paddleup.support.AbstractPostgresIT;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** AC-11, AC-12: the log keeps a gap-free seq, also after a failed insert. */
class AuctionRepositoriesIT extends AbstractPostgresIT {

  @Autowired JdbcTemplate jdbc;
  @Autowired AuctionRepository headers;
  @Autowired AuctionEventRepository events;
  @Autowired AuctionLotRepository lots;
  @Autowired TransactionTemplate tx;

  static final Instant AT = Instant.parse("2030-01-01T10:00:00Z");

  private UUID user() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, email, display_name, avatar, created_at, updated_at)"
            + " VALUES (?, ?, 'Ann', 'preset-01', now(), now())",
        id,
        "repo-" + id + "@example.test");
    return id;
  }

  private UUID room(UUID commissioner) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO rooms (id, state, commissioner_user_id, pack_id, pack_version, preset_id,"
            + " pack_snapshot, config, created_at, updated_at)"
            + " VALUES (?, 'setup', ?, 'p', '1', 'x', '{}'::jsonb, '{}'::jsonb, now(), now())",
        id,
        commissioner);
    return id;
  }

  private AuctionHeader header(UUID room, UUID by, long lastSeq) {
    return new AuctionHeader(room, Phase.SCHEDULED, AT, by, null, lastSeq);
  }

  /** Appends one event and moves the header, the way the runner does inside one transaction. */
  private void persist(UUID room, UUID by, long from, AuctionEvent... batch) {
    tx.executeWithoutResult(
        s -> {
          for (AuctionEvent e : batch) {
            events.append(room, e);
          }
          long to = batch[batch.length - 1].seq();
          if (!headers.upsert(header(room, by, to), from, AT)) {
            throw new IllegalStateException("stale header");
          }
        });
  }

  @Test
  void seqStaysGapFreeAcrossAppendsAndAfterAFailedInsert() {
    UUID by = user();
    UUID room = room(by);
    persist(room, by, 0, Events.scheduled(1, AT, by, AT.plusSeconds(60)));
    persist(room, by, 1, Events.startRefused(2, AT, "a"));

    // A batch whose second event repeats seq 3 fails as a whole: neither row nor header survives.
    assertThatThrownBy(
            () ->
                persist(
                    room, by, 2, Events.startRefused(3, AT, "b"), Events.startRefused(3, AT, "c")))
        .isInstanceOf(DuplicateKeyException.class);
    assertThat(events.all(room)).extracting(AuctionEvent::seq).containsExactly(1L, 2L);
    assertThat(headers.find(room).orElseThrow().lastSeq()).isEqualTo(2);

    persist(room, by, 2, Events.startRefused(3, AT, "d"));

    assertThat(events.all(room)).extracting(AuctionEvent::seq).containsExactly(1L, 2L, 3L);
    assertThat(headers.find(room).orElseThrow().lastSeq()).isEqualTo(3);
  }

  @Test
  void aSecondWriterWithTheSameSeqOrAStaleHeaderFails() {
    UUID by = user();
    UUID room = room(by);
    persist(room, by, 0, Events.scheduled(1, AT, by, AT.plusSeconds(60)));

    assertThatThrownBy(() -> persist(room, by, 0, Events.startRefused(1, AT, "x")))
        .isInstanceOf(DuplicateKeyException.class);
    // A stale header refuses the move even when the event itself is new.
    assertThatThrownBy(() -> persist(room, by, 5, Events.startRefused(2, AT, "x")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(events.all(room)).hasSize(1);
  }

  @Test
  void afterReturnsTheEventsPastASeqInOrderWithTheirPayloads() {
    UUID by = user();
    UUID room = room(by);
    persist(room, by, 0, Events.scheduled(1, AT, by, AT.plusSeconds(60)));
    UUID intent = UUID.randomUUID();
    UUID franchise = UUID.randomUUID();
    persist(
        room,
        by,
        1,
        Events.bidAccepted(2, AT.plusSeconds(1), 4, franchise, by, intent, 75, AT.plusSeconds(11)));
    persist(room, by, 2, Events.lotUnsold(3, AT.plusSeconds(2), 4));

    List<AuctionEvent> after = events.after(room, 1, 10);

    assertThat(after).extracting(AuctionEvent::seq).containsExactly(2L, 3L);
    AuctionEvent bid = after.get(0);
    assertThat(bid.lot()).isEqualTo(4);
    assertThat(bid.franchiseId()).isEqualTo(franchise);
    assertThat(bid.number("amount")).isEqualTo(75);
    assertThat(bid.instant("deadline")).isEqualTo(AT.plusSeconds(11));
    assertThat(events.after(room, 1, 1)).hasSize(1);
    assertThat(events.after(room, 3, 10)).isEmpty();
    assertThat(events.byIntent(room, by, intent))
        .hasValueSatisfying(e -> assertThat(e.seq()).isEqualTo(2));
    assertThat(events.byIntent(room, by, UUID.randomUUID())).isEmpty();
    assertThat(LongStream.rangeClosed(1, 3).boxed())
        .containsExactlyElementsOf(events.all(room).stream().map(AuctionEvent::seq).toList());
  }

  @Test
  void headersListOnlyUnendedAuctionsAndLotsRoundTrip() {
    UUID by = user();
    UUID room = room(by);
    persist(room, by, 0, Events.scheduled(1, AT, by, AT.plusSeconds(60)));
    assertThat(headers.unended()).contains(room);
    tx.executeWithoutResult(
        s ->
            headers.upsert(
                new AuctionHeader(room, Phase.ENDED, AT, by, null, 1), 1, AT.plusSeconds(1)));
    assertThat(headers.unended()).doesNotContain(room);

    Lot lot = new Lot(1, UUID.randomUUID(), "Ann", "Chile", "r1", List.of("a", "b"), "S", 0, 20);
    lots.insertAll(room, List.of(lot));

    assertThat(lots.byRoom(room)).containsExactly(lot);
  }
}
