package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** AC-10, AC-4: GET /api/rooms carries a scheduled auction's start and the server clock. */
class MyRoomsStartIT extends AbstractAuctionIT {

  private JsonNode summary(Person who, String roomId) {
    JsonNode list = body(get(who, "/api/rooms"));
    return StreamSupport.stream(list.spliterator(), false)
        .filter(n -> n.get("id").asText().equals(roomId))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aScheduledRoomCarriesItsStartAndServerNow() {
    Room room = room(2, 6);
    Instant now = T0.minusSeconds(3600);
    clock.freeze(now);
    assertThat(schedule(room, T0).getStatusCode().is2xxSuccessful()).isTrue();

    JsonNode summary = summary(room.manager(0), room.id());

    assertThat(summary.get("state").asText()).isEqualTo("auction_scheduled");
    assertThat(Instant.parse(summary.get("auctionStartsAt").asText())).isEqualTo(T0);
    assertThat(Instant.parse(summary.get("serverNow").asText())).isEqualTo(now);
  }

  @Test
  void aSetupRoomHasNoStart() {
    Room room = room(2, 6);

    JsonNode summary = summary(room.commissioner(), room.id());

    assertThat(summary.get("state").asText()).isEqualTo("setup");
    assertThat(summary.get("auctionStartsAt").isNull()).isTrue();
    assertThat(summary.has("serverNow")).isTrue();
  }

  @Test
  void aLiveRoomHasNoStart() {
    Room room = room(2, 6);
    goLive(room);

    JsonNode summary = summary(room.commissioner(), room.id());

    assertThat(summary.get("state").asText()).isEqualTo("auction_live");
    assertThat(summary.get("auctionStartsAt").isNull()).isTrue();
  }

  @Test
  void aScheduledRoomWithNoAuctionRowHasNoStart() {
    Room room = room(2, 6);
    jdbc.update(
        "UPDATE rooms SET state = 'auction_scheduled' WHERE id = ?", UUID.fromString(room.id()));

    JsonNode summary = summary(room.commissioner(), room.id());

    assertThat(summary.get("state").asText()).isEqualTo("auction_scheduled");
    assertThat(summary.get("auctionStartsAt").isNull()).isTrue();
  }
}
