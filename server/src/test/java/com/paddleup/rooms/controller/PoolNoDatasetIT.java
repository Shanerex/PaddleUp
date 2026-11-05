package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.rooms.AbstractRoomsIT;
import com.paddleup.rooms.dto.RoomState;
import com.paddleup.rooms.exception.RoomConflictException;
import com.paddleup.rooms.service.RoomLifecycle;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

/**
 * AC-6, AC-7: a pack that no admin imported season data for. No test imports for this pack, so its
 * rooms never get a copy.
 */
@TestPropertySource(properties = "paddleup.packs.dir=src/test/resources/packs/alt")
class PoolNoDatasetIT extends AbstractRoomsIT {

  @Autowired RoomLifecycle lifecycle;

  @Test
  void withNoDatasetThereIsNoCopyAndThePoolReportsEmpty() {
    Person commissioner = person();
    String roomId = createRoom(commissioner, "stub-volleyball", "club");

    var response = get(commissioner, "/api/rooms/" + roomId + "/pool");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var pool = body(response);
    assertThat(pool.get("hasPool").asBoolean()).isFalse();
    assertThat(pool.get("version").asInt()).isZero();
    assertThat(pool.get("datasetVersion").isNull()).isTrue();
    assertThat(pool.get("latestDatasetVersion").isNull()).isTrue();
    assertThat(pool.get("players")).isEmpty();
    assertThat(pool.get("sets")).isEmpty();
    assertThat(pool.get("canEdit").asBoolean()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM room_pools WHERE room_id = ?",
                Integer.class,
                UUID.fromString(roomId)))
        .isZero();
  }

  @Test
  void auctionStartIsRefusedWhileTheRoomHasNoPoolAndTheRoomStaysScheduled() {
    Person commissioner = person();
    String roomId = createRoom(commissioner, "stub-volleyball", "club");
    insertManager(roomId, "sky");
    insertManager(roomId, "teal");
    assertThat(
            post(
                    commissioner,
                    "/api/rooms/" + roomId + "/transitions",
                    Map.of("to", "auction_scheduled"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    assertThatThrownBy(
            () ->
                lifecycle.advance(
                    UUID.fromString(roomId),
                    RoomState.AUCTION_LIVE,
                    new UserId(userId(commissioner))))
        .isInstanceOfSatisfying(
            RoomConflictException.class,
            e -> {
              assertThat(e.code()).isEqualTo("illegal_state");
              assertThat(e.getMessage()).contains("No player pool");
            });

    assertThat(
            jdbc.queryForObject(
                "SELECT state FROM rooms WHERE id = ?", String.class, UUID.fromString(roomId)))
        .isEqualTo("auction_scheduled");
  }

  @Test
  void resetWithNoSeasonDataIs409AndStillNoCopy() {
    Person commissioner = person();
    String roomId = createRoom(commissioner, "stub-volleyball", "club");

    var response = post(commissioner, "/api/rooms/" + roomId + "/pool/reset", Map.of("version", 0));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(response).get("error").asText()).isEqualTo("illegal_state");
    assertThat(body(get(commissioner, "/api/rooms/" + roomId + "/pool")).get("hasPool").asBoolean())
        .isFalse();
  }
}
