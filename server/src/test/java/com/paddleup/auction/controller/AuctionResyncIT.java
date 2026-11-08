package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import com.paddleup.auction.StompProbe;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.repository.AuctionEventRepository;
import com.paddleup.auction.repository.AuctionLotRepository;
import com.paddleup.auction.service.AuctionReducer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/** AC-13: a reconnecting client and a new tab get the server's state; a gap triggers a refetch. */
class AuctionResyncIT extends AbstractAuctionIT {

  @LocalServerPort int port;
  @Autowired AuctionEventRepository eventLog;
  @Autowired AuctionLotRepository lotRepo;

  /** The state rebuilt straight from the stored log, the way boot recovery does. */
  private AuctionState fromLog(Room room) {
    UUID id = UUID.fromString(room.id());
    Map<UUID, com.paddleup.auction.model.Lot> catalog = new LinkedHashMap<>();
    lotRepo.byRoom(id).forEach(l -> catalog.put(l.playerId(), l));
    return AuctionReducer.replay(AuctionState.initial(id, catalog), eventLog.all(id));
  }

  private void assertEqualToLog(JsonNode s, AuctionState log) {
    assertThat(s.get("lastSeq").asLong()).isEqualTo(log.lastSeq());
    assertThat(s.get("currentBid").asLong()).isEqualTo(log.currentBid());
    assertThat(s.get("lot").get("no").asInt()).isEqualTo(log.currentLot());
    assertThat(s.get("lotOpen").asBoolean()).isEqualTo(log.lotOpen());
    assertThat(s.get("deadline").asText()).isEqualTo(log.deadline().toString());
    assertThat(s.get("leaderFranchiseId").asText()).isEqualTo(log.leader().toString());
    assertThat(s.get("passedBy")).hasSize(log.passedBy().size());
    for (JsonNode standing : s.get("standings")) {
      UUID franchise = UUID.fromString(standing.get("franchiseId").asText());
      assertThat(standing.get("purse").asLong()).isEqualTo(log.purses().get(franchise));
      assertThat(standing.get("squad")).hasSize(log.squads().get(franchise).size());
    }
  }

  @Test
  void aReconnectingMemberAndANewTabGetStateEqualToTheServers() throws Exception {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    bid(room, room.manager(1), 1, 25);
    pass(room, room.manager(0), 1);
    advance(10);
    tick();
    advance(5);
    tick();
    bid(room, room.manager(0), 2, 20);
    advance(2);

    JsonNode original = state(room, room.manager(0));
    JsonNode reconnected = state(room, room.manager(0));
    JsonNode newTab = state(room, room.manager(1));
    JsonNode commissioner = state(room, room.commissioner());

    AuctionState log = fromLog(room);
    for (JsonNode s : List.of(original, reconnected, newTab, commissioner)) {
      assertEqualToLog(s, log);
      assertThat(s.get("serverNow").asText()).isEqualTo(clock.instant().toString());
    }
    assertThat(newTab).isEqualTo(original);
    assertThat(commissioner).isEqualTo(original);
    // Lot 1 was sold to franchise 1 at 25.
    for (JsonNode standing : original.get("standings")) {
      if (standing.get("franchiseId").asText().equals(room.franchise(1).toString())) {
        assertThat(standing.get("purse").asLong()).isEqualTo(975);
      }
    }
    assertThat(original.get("lastResult").get("lot").asInt()).isEqualTo(1);
  }

  /** What a careful client does: track seq, and refetch the log when it sees a jump. */
  private static final class Tracker {
    long last;
    int refetches;
    final List<Long> seen = new ArrayList<>();

    void apply(JsonNode event) {
      long seq = event.get("seq").asLong();
      if (seq <= last) {
        return;
      }
      seen.add(seq);
      last = seq;
    }
  }

  @Test
  void aClientThatMissesOneEventDetectsTheGapAndRefetchesToEqualState() throws Exception {
    Room room = room(2, 6);
    goLive(room);
    Tracker client = new Tracker();
    for (JsonNode e : events(room, room.manager(0), 0).get("events")) {
      client.apply(e);
    }
    try (StompProbe probe = StompProbe.connect(json, port, room.manager(0).cookie(), null, null)) {
      probe.subscribe("/topic/auctions/" + room.id());
      Thread.sleep(300);

      bid(room, room.manager(1), 1, 20);
      bid(room, room.manager(0), 1, 25);
      bid(room, room.manager(1), 1, 30);

      List<JsonNode> arrived = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        arrived.add(probe.next(3000));
      }
      assertThat(arrived).doesNotContainNull();
      // The second event is lost on the way: the client never sees it.
      for (JsonNode event : List.of(arrived.get(0), arrived.get(2))) {
        long seq = event.get("seq").asLong();
        if (seq > client.last + 1) {
          client.refetches++;
          for (JsonNode missed : events(room, room.manager(0), client.last).get("events")) {
            client.apply(missed);
          }
        }
        client.apply(event);
      }
    }

    assertThat(client.refetches).isEqualTo(1);
    long serverSeq = state(room, room.manager(0)).get("lastSeq").asLong();
    assertThat(client.last).isEqualTo(serverSeq);
    assertThat(client.seen)
        .containsExactlyElementsOf(
            eventLog.all(UUID.fromString(room.id())).stream().map(e -> e.seq()).toList());
    assertEqualToLog(state(room, room.manager(0)), fromLog(room));
  }
}
