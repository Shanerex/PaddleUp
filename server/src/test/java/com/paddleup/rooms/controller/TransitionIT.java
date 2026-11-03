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

/** AC-13, AC-14, AC-15: explicit, attributed, forward-only state changes. */
class TransitionIT extends AbstractRoomsIT {

  @Autowired RoomLifecycle lifecycle;

  private String url(String roomId) {
    return "/api/rooms/" + roomId + "/transitions";
  }

  private String stateOf(String roomId) {
    return jdbc.queryForObject(
        "SELECT state FROM rooms WHERE id = ?", String.class, UUID.fromString(roomId));
  }

  /** A room in setup with two managers. */
  private String readyRoom(Person commissioner) {
    String roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    joinNew(roomId, code, "crimson");
    joinNew(roomId, code, "sky");
    return roomId;
  }

  @Test
  void leavingSetupWithTooFewFranchisesIs409ForACommissionerWithOrWithoutOne() {
    Person adminOnly = person();
    String roomA = createRoom(adminOnly);
    joinNew(roomA, issueInvite(adminOnly, roomA), "sky");
    Person player = person();
    String roomB = createRoom(player);
    post(player, "/api/rooms/" + roomB + "/franchises", franchiseBody("olive"));

    for (var pair : java.util.List.of(Map.entry(adminOnly, roomA), Map.entry(player, roomB))) {
      var response = post(pair.getKey(), url(pair.getValue()), Map.of("to", "retention"));
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(body(response).get("error").asText()).isEqualTo("illegal_state");
      assertThat(stateOf(pair.getValue())).isEqualTo("setup");
    }
  }

  @Test
  void withEnoughFranchisesSetupGoesToRetentionOrStraightToAuctionScheduled() {
    Person a = person();
    String roomA = readyRoom(a);
    Person b = person();
    String roomB = readyRoom(b);

    var toRetention = post(a, url(roomA), Map.of("to", "retention"));
    var skip = post(b, url(roomB), Map.of("to", "auction_scheduled"));

    assertThat(toRetention.getStatusCode()).as(toRetention.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(body(toRetention).get("from").asText()).isEqualTo("setup");
    assertThat(body(toRetention).get("to").asText()).isEqualTo("retention");
    assertThat(skip.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stateOf(roomA)).isEqualTo("retention");
    assertThat(stateOf(roomB)).isEqualTo("auction_scheduled");
    assertThat(post(a, url(roomA), Map.of("to", "auction_scheduled")).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void skippingPastAuctionScheduledOrMovingBackwardOrAdvancingFromCompletedIs409ForEveryCaller() {
    Person c = person();
    String roomId = readyRoom(c);
    Person manager = insertManager(roomId, "teal");

    for (String to : java.util.List.of("auction_live", "season_live", "completed", "setup")) {
      var response = post(c, url(roomId), Map.of("to", to));
      assertThat(response.getStatusCode()).as(to).isEqualTo(HttpStatus.CONFLICT);
      assertThat(body(response).get("error").asText()).isEqualTo("illegal_state");
    }
    post(c, url(roomId), Map.of("to", "retention"));
    assertThat(post(c, url(roomId), Map.of("to", "setup")).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(stateOf(roomId)).isEqualTo("retention");
    assertThat(post(manager, url(roomId), Map.of("to", "auction_scheduled")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    jdbc.update("UPDATE rooms SET state = 'completed' WHERE id = ?", UUID.fromString(roomId));
    for (String to : java.util.List.of("completed", "season_live", "retention")) {
      assertThat(post(c, url(roomId), Map.of("to", to)).getStatusCode())
          .isEqualTo(HttpStatus.CONFLICT);
    }
    assertThatThrownBy(
            () ->
                lifecycle.advance(
                    UUID.fromString(roomId), RoomState.COMPLETED, new UserId(UUID.randomUUID())))
        .isInstanceOf(RoomConflictException.class);
  }

  @Test
  void theAuctionCannotBeStartedOverRestBecauseOnlyTheRoomsServiceMovesIt() {
    Person c = person();
    String roomId = readyRoom(c);
    post(c, url(roomId), Map.of("to", "auction_scheduled"));

    var response = post(c, url(roomId), Map.of("to", "auction_live"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(stateOf(roomId)).isEqualTo("auction_scheduled");
  }

  @Test
  void aBadTargetIs400() {
    Person c = person();
    String roomId = readyRoom(c);

    assertThat(post(c, url(roomId), Map.of("to", "bogus")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(c, url(roomId), Map.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void theRoomsServiceMovesAScheduledAuctionThroughLiveSeasonAndCompleted() {
    Person c = person();
    String roomId = readyRoom(c);
    post(c, url(roomId), Map.of("to", "auction_scheduled"));
    UserId actor = new UserId(userId(c));
    UUID id = UUID.fromString(roomId);

    assertThat(lifecycle.advance(id, RoomState.AUCTION_LIVE, actor).state())
        .isEqualTo(RoomState.AUCTION_LIVE);
    assertThat(lifecycle.advance(id, RoomState.SEASON_LIVE, actor).state())
        .isEqualTo(RoomState.SEASON_LIVE);
    assertThat(lifecycle.advance(id, RoomState.COMPLETED, actor).state())
        .isEqualTo(RoomState.COMPLETED);
    assertThat(stateOf(roomId)).isEqualTo("completed");
    assertThatThrownBy(() -> lifecycle.advance(id, RoomState.SEASON_LIVE, actor))
        .isInstanceOf(RoomConflictException.class);
  }

  @Test
  void theRoomsServiceStillRefusesAnIllegalSkip() {
    Person c = person();
    String roomId = readyRoom(c);
    UserId actor = new UserId(userId(c));

    assertThatThrownBy(
            () -> lifecycle.advance(UUID.fromString(roomId), RoomState.AUCTION_LIVE, actor))
        .isInstanceOf(RoomConflictException.class)
        .extracting(e -> ((RoomConflictException) e).code())
        .isEqualTo("illegal_state");
    assertThat(stateOf(roomId)).isEqualTo("setup");
  }

  @Test
  void goingLiveRevokesTheActiveInvite() {
    Person c = person();
    String roomId = readyRoom(c);
    String code = issueInvite(c, roomId);
    post(c, url(roomId), Map.of("to", "auction_scheduled"));
    assertThat(get(c, "/api/rooms/" + roomId + "/invite").getStatusCode()).isEqualTo(HttpStatus.OK);

    lifecycle.advance(UUID.fromString(roomId), RoomState.AUCTION_LIVE, new UserId(userId(c)));

    assertThat(get(c, "/api/rooms/" + roomId + "/invite").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    var late = join(person(), code, "olive");
    assertThat(late.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(body(late).get("error").asText()).isEqualTo("invalid_invite");
  }

  @Test
  void everyTransitionIsLoggedWithItsActorAndReadableByAnyMember() {
    Person c = person();
    String roomId = readyRoom(c);
    Person manager = insertManager(roomId, "teal");
    post(c, url(roomId), Map.of("to", "retention"));
    post(c, url(roomId), Map.of("to", "auction_scheduled"));
    lifecycle.advance(UUID.fromString(roomId), RoomState.AUCTION_LIVE, new UserId(userId(c)));

    var response = get(manager, url(roomId));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var history = body(response);
    assertThat(history).hasSize(3);
    assertThat(history.get(0).get("from").asText()).isEqualTo("setup");
    assertThat(history.get(0).get("to").asText()).isEqualTo("retention");
    assertThat(history.get(2).get("to").asText()).isEqualTo("auction_live");
    assertThat(history.get(0).get("actor").get("id").asText()).isEqualTo(userId(c).toString());
    assertThat(history.get(0).get("occurredAt").asText()).endsWith("Z");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM room_log WHERE room_id = ? AND kind = 'transition'"
                    + " AND actor_user_id IS NOT NULL",
                Integer.class,
                UUID.fromString(roomId)))
        .isEqualTo(3);
  }

  @Test
  void aNonMemberCannotReadOrMakeTransitions() {
    Person c = person();
    String roomId = readyRoom(c);

    assertThat(get(person(), url(roomId)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(post(person(), url(roomId), Map.of("to", "retention")).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void noUpdateOrDeletePathExistsOnTheLog() {
    Person c = person();
    String roomId = readyRoom(c);
    post(c, url(roomId), Map.of("to", "retention"));

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE room_log SET kind = 'x' WHERE room_id = ?", UUID.fromString(roomId)))
        .hasMessageContaining("append-only");
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM room_log WHERE room_id = ?", UUID.fromString(roomId)))
        .hasMessageContaining("append-only");
    for (String method : new String[] {"PUT", "PATCH", "DELETE"}) {
      var response =
          call(
              org.springframework.http.HttpMethod.valueOf(method),
              url(roomId),
              c.cookie(),
              Map.of("to", "retention"));
      assertThat(response.getStatusCode().is2xxSuccessful()).as(method).isFalse();
    }
  }
}
