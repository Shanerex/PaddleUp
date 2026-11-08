package com.paddleup.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.rooms.AbstractRoomsIT;
import com.paddleup.rooms.dto.RoomState;
import com.paddleup.rooms.exception.RoomNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/** AC-1, AC-2, AC-17: the one port the auction uses to read a room. */
class AuctionRoomPortIT extends AbstractRoomsIT {

  @Autowired AuctionRoomPort port;

  Person commissioner;
  Person manager;
  String roomId;

  @BeforeEach
  void room() {
    importSeason("cricket-t20", SEASON_CSV);
    commissioner = person();
    roomId = createRoom(commissioner);
    manager = insertManager(roomId, "sky");
  }

  private UUID id() {
    return UUID.fromString(roomId);
  }

  private UserId user(Person who) {
    return new UserId(userId(who));
  }

  @Test
  void aCommissionerAndAManagerHaveTheirRolesAndANonMemberIsNotFound() {
    var asCommissioner = port.access(id(), user(commissioner));
    var asManager = port.access(id(), user(manager));

    assertThat(asCommissioner.commissioner()).isTrue();
    assertThat(asCommissioner.franchiseId()).isNull();
    assertThat(asCommissioner.state()).isEqualTo(RoomState.SETUP);
    assertThat(asManager.commissioner()).isFalse();
    assertThat(asManager.franchiseId()).isNotNull();
    assertThat(asManager.franchiseCount()).isEqualTo(1);
    assertThatThrownBy(() -> port.access(id(), user(person())))
        .isInstanceOf(RoomNotFoundException.class);
  }

  @Test
  void aRoomWithNoPoolCopyIsRefused() {
    jdbc.update("DELETE FROM room_pool_players WHERE room_id = ?", id());
    jdbc.update("DELETE FROM room_pools WHERE room_id = ?", id());
    insertManager(roomId, "olive");

    assertThat(port.auctionRefusal(id())).hasValueSatisfying(r -> assertThat(r).contains("pool"));
  }

  @Test
  void aPoolBelowTheMinimumIsRefusedAndAPoolThatFillsEverySquadIsNot() {
    insertManager(roomId, "olive");
    // Six players against 2 franchises x squadMin 18.
    assertThat(port.auctionRefusal(id()))
        .hasValueSatisfying(r -> assertThat(r).contains("6 players").contains("36"));

    ensurePool(roomId);

    assertThat(port.auctionRefusal(id())).isEmpty();
  }

  @Test
  void tooFewFranchisesAreRefusedEvenWithAFullPool() {
    ensurePool(roomId);

    assertThat(port.auctionRefusal(id()))
        .hasValueSatisfying(r -> assertThat(r).contains("at least 2 franchises"));
  }

  @Test
  void playersComeInSetOrderThenPosition() {
    var order = List.of("Uncapped", "Marquee", "Capped Batters");
    int v = body(get(commissioner, "/api/rooms/" + roomId + "/pool")).get("version").asInt();
    var reordered =
        put(
            commissioner,
            "/api/rooms/" + roomId + "/pool/sets",
            Map.of("version", v, "order", order));
    assertThat(reordered.getStatusCode()).as(reordered.getBody()).isEqualTo(HttpStatus.OK);

    var inputs = port.liveInputs(id());

    assertThat(inputs.setOrder()).containsExactlyElementsOf(order);
    assertThat(inputs.players().stream().map(p -> p.name()))
        .containsExactly("Ed Fu", "Gus Ho", "Ann Lee", "Bo Ray", "Cy Day", "Di Ox");
    assertThat(inputs.franchises()).hasSize(1);
    assertThat(inputs.config().openSeconds()).isEqualTo(20);
  }
}
