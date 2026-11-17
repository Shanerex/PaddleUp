package com.paddleup.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-10, AC-11, AC-15: main-done, the unsold pool and the accelerated round. */
class AuctionAcceleratedIT extends AbstractAuctionIT {

  private JsonNode view(Room room) {
    return state(room, room.commissioner());
  }

  private String phase(Room room) {
    return view(room).get("phase").asText();
  }

  /** Lot {@code lot} is open: the manager takes it at base price and its gap passes. */
  private void sell(Room room, int manager, int lot) {
    assertThat(outcome(bid(room, room.manager(manager), lot, 20))).isEqualTo("accepted");
    advance(10);
    tick();
    advance(5);
    tick();
  }

  /** Lot {@code lot} is open and nobody bids; its gap passes. */
  private void leaveUnsold(Room room) {
    advance(20);
    tick();
    advance(5);
    tick();
  }

  /** Lots 1..{@code sold} go to manager 0, the rest stay unsold; the room ends in main-done. */
  private Room mainDone(int players, int sold) {
    Room room = room(2, players);
    goLive(room);
    for (int lot = 1; lot <= sold; lot++) {
      sell(room, 0, lot);
    }
    for (int lot = sold + 1; lot <= players; lot++) {
      leaveUnsold(room);
    }
    assertThat(phase(room)).isEqualTo("main_done");
    return room;
  }

  private static List<Integer> numbers(JsonNode array) {
    List<Integer> out = new ArrayList<>();
    array.forEach(n -> out.add(n.asInt()));
    return out;
  }

  private static List<Integer> lotNos(JsonNode cards) {
    List<Integer> out = new ArrayList<>();
    cards.forEach(c -> out.add(c.get("no").asInt()));
    return out;
  }

  // ---- T-10: the end-of-main choice ----

  @Test
  void onlyTheCommissionerCanOpenTheRoundOrEndTheAuction() {
    Room room = mainDone(3, 1);
    long seq = lastSeq(room);

    for (String action : new String[] {"open_round", "end_auction"}) {
      var response = controlAs(room, room.manager(0), UUID.randomUUID(), action);
      assertThat(response.getStatusCode()).as(action).isEqualTo(HttpStatus.FORBIDDEN);
    }

    assertThat(lastSeq(room)).isEqualTo(seq);
    assertThat(phase(room)).isEqualTo("main_done");
  }

  @Test
  void endAuctionFromMainDoneEndsIt() {
    Room room = mainDone(3, 1);

    assertThat(outcome(control(room, "end_auction"))).isEqualTo("accepted");

    assertThat(phase(room)).isEqualTo("ended");
    assertThat(eventCount(room, "auction_ended")).isEqualTo(1);
    assertThat(outcome(control(room, "open_round"))).isEqualTo("rejected:wrong_phase");
    assertThat(outcome(control(room, "end_auction"))).isEqualTo("rejected:wrong_phase");
  }

  @Test
  void openRoundMovesToNominatingAndIsRefusedOutsideMainDone() {
    Room live = room(2, 3);
    goLive(live);
    assertThat(outcome(control(live, "open_round"))).isEqualTo("rejected:wrong_phase");
    assertThat(outcome(control(live, "end_round"))).isEqualTo("rejected:wrong_phase");
    assertThat(outcome(control(live, "end_auction"))).isEqualTo("rejected:wrong_phase");

    Room room = mainDone(3, 1);
    assertThat(outcome(control(room, "end_round"))).isEqualTo("rejected:wrong_phase");
    assertThat(outcome(control(room, "open_round"))).isEqualTo("accepted");

    JsonNode nominating = view(room);
    assertThat(nominating.get("phase").asText()).isEqualTo("nominating");
    assertThat(nominating.get("round").asText()).isEqualTo("accelerated");
    assertThat(outcome(control(room, "open_round"))).isEqualTo("rejected:wrong_phase");
    var bid = bid(room, room.manager(0), 2, 20);
    assertThat(bid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(bid).get("error").asText()).isEqualTo("nominating");
  }

  @Test
  void anEmptyAcceleratedRoundEndsTheAuctionAndASecondRoundIsRejected() {
    Room room = mainDone(3, 1);
    control(room, "open_round");

    assertThat(outcome(control(room, "end_round"))).isEqualTo("accepted");

    assertThat(phase(room)).isEqualTo("ended");
    assertThat(eventCount(room, "round_started")).isZero();
    assertThat(eventCount(room, "auction_ended")).isEqualTo(1);
    assertThat(outcome(control(room, "open_round"))).isEqualTo("rejected:wrong_phase");
  }

  // ---- T-11, T-12: the unsold pool, picks, counts and the round ----

  @Test
  void theUnsoldPoolHoldsNoBidLotsAndNeverSoldOnes() {
    Room room = mainDone(4, 2);

    JsonNode done = view(room);

    assertThat(numbers(done.get("unsold"))).containsExactly(3, 4);
    assertThat(lotNos(done.get("unsoldPool"))).containsExactly(3, 4);
    assertThat(done.get("unsoldPool").get(0).get("name").asText()).isEqualTo("Player 3");
  }

  @Test
  void picksWithinOpenSlotsAreAcceptedBeyondAreRejectedAndOnlyInNominating() {
    Room room = mainDone(6, 4);
    // Manager 0 holds 4 of 5 places: one slot left. Manager 1 holds none: both unsold lots.
    assertThat(outcome(picks(room, room.manager(1), List.of(5)))).isEqualTo("rejected:wrong_phase");
    control(room, "open_round");

    assertThat(outcome(picks(room, room.manager(0), List.of(5, 6))))
        .isEqualTo("rejected:over_slots");
    assertThat(outcome(picks(room, room.manager(0), List.of(5)))).isEqualTo("accepted");
    assertThat(outcome(picks(room, room.manager(1), List.of(5, 6)))).isEqualTo("accepted");
    assertThat(outcome(picks(room, room.manager(1), List.of(3)))).isEqualTo("rejected:not_unsold");
    assertThat(outcome(picks(room, room.manager(1), List.of(5, 5))))
        .isEqualTo("rejected:not_unsold");
    assertThat(outcome(picks(room, room.commissioner(), List.of(5))))
        .isEqualTo("rejected:not_a_franchise");
    assertThat(eventCount(room, "picks_submitted")).isEqualTo(2);
  }

  @Test
  void everyMemberSeesPickCountsAndOnlyTheirOwnPicksAndSlots() {
    Room room = mainDone(6, 4);
    control(room, "open_round");
    picks(room, room.manager(0), List.of(6));
    picks(room, room.manager(1), List.of(5, 6));

    for (Person who : List.of(room.manager(0), room.manager(1), room.commissioner())) {
      JsonNode s = state(room, who);
      assertThat(s.get("pickCounts")).hasSize(2);
      for (JsonNode c : s.get("pickCounts")) {
        assertThat(c.get("count").asInt()).isEqualTo(c.get("lot").asInt() == 6 ? 2 : 1);
      }
      assertThat(lotNos(s.get("unsoldPool"))).containsExactly(5, 6);
      assertThat(s.get("waiting")).isEmpty();
    }
    JsonNode m0 = state(room, room.manager(0));
    assertThat(numbers(m0.get("myPicks"))).containsExactly(6);
    assertThat(m0.get("openSlots").asInt()).isEqualTo(1);
    assertThat(numbers(state(room, room.manager(1)).get("myPicks"))).containsExactly(5, 6);
    assertThat(state(room, room.manager(1)).get("openSlots").asInt()).isEqualTo(2);
    assertThat(state(room, room.commissioner()).has("openSlots")).isFalse();
    assertThat(state(room, room.commissioner()).get("myPicks")).isEmpty();
  }

  @Test
  void waitingListsFranchisesThatHaveNotSentPicks() {
    Room room = mainDone(6, 2);
    control(room, "open_round");
    assertThat(view(room).get("waiting")).hasSize(2);

    picks(room, room.manager(1), List.of());

    JsonNode s = view(room);
    assertThat(s.get("waiting")).hasSize(1);
    assertThat(s.get("waiting").get(0).asText()).isEqualTo(room.franchise(0).toString());
  }

  @Test
  void aFranchiseWithNoOpenSlotsIsNotWaitingForPicks() {
    Room room = mainDone(7, 5);
    control(room, "open_round");
    // Manager 0 holds 5 of 5 places: it cannot pick, so the round does not wait for it.
    assertThat(state(room, room.manager(0)).get("openSlots").asInt()).isZero();

    JsonNode before = view(room);
    assertThat(before.get("waiting")).hasSize(1);
    assertThat(before.get("waiting").get(0).asText()).isEqualTo(room.franchise(1).toString());

    picks(room, room.manager(1), List.of(6));
    assertThat(view(room).get("waiting")).isEmpty();
  }

  @Test
  void endRoundPlaysThePickedLotsMostPickedFirstWithTiesByLotNumber() {
    Room room = mainDone(7, 4);
    control(room, "open_round");
    // Lots 5, 6 and 7 are unsold. Counts: 7 has two picks, 5 and 6 one each.
    picks(room, room.manager(0), List.of(7));
    picks(room, room.manager(1), List.of(6, 7, 5));

    assertThat(outcome(control(room, "end_round"))).isEqualTo("accepted");

    JsonNode live = view(room);
    assertThat(live.get("phase").asText()).isEqualTo("live");
    assertThat(live.get("round").asText()).isEqualTo("accelerated");
    // New lot numbers follow the 7 main lots: 8 = lot 7's player, then 5's, then 6's.
    assertThat(live.get("lot").get("no").asInt()).isEqualTo(8);
    assertThat(live.get("lot").get("name").asText()).isEqualTo("Player 7");
    assertThat(live.get("lotOpen").asBoolean()).isTrue();
    assertThat(live.get("currentBid").asLong()).isZero();
    assertThat(numbers(live.get("unsold"))).isEmpty();

    advance(20);
    tick();
    advance(5);
    tick();
    assertThat(view(room).get("lot").get("no").asInt()).isEqualTo(9);
    assertThat(view(room).get("lot").get("name").asText()).isEqualTo("Player 5");
    advance(20);
    tick();
    advance(5);
    tick();
    assertThat(view(room).get("lot").get("name").asText()).isEqualTo("Player 6");
    // Three picked lots run, the last being lot 6's player.
    advance(20);
    tick();
    assertThat(phase(room)).isEqualTo("live");
    advance(5);
    tick();
    assertThat(phase(room)).isEqualTo("ended");
    assertThat(eventCount(room, "main_done")).isEqualTo(1);
    assertThat(eventCount(room, "auction_ended")).isEqualTo(1);
  }

  @Test
  void anAcceleratedLotCarriesItsPositionTheRoundSizeAndItsPickCountForEveryMember() {
    Room room = mainDone(7, 4);
    control(room, "open_round");
    picks(room, room.manager(0), List.of(7));
    picks(room, room.manager(1), List.of(6, 7, 5));
    assertThat(view(room).has("accelSize")).isFalse();
    control(room, "end_round");

    for (Person who : List.of(room.manager(0), room.manager(1), room.commissioner())) {
      JsonNode first = state(room, who);
      assertThat(first.get("lot").get("name").asText()).isEqualTo("Player 7");
      assertThat(first.get("accelPosition").asInt()).isEqualTo(1);
      assertThat(first.get("accelSize").asInt()).isEqualTo(3);
      assertThat(first.get("lotPicks").asInt()).isEqualTo(2);
    }
    // The counts stay after the round starts although the picked lots left the pool.
    assertThat(view(room).get("pickCounts")).isEmpty();

    advance(20);
    tick();
    advance(5);
    tick();

    JsonNode second = view(room);
    assertThat(second.get("lot").get("name").asText()).isEqualTo("Player 5");
    assertThat(second.get("accelPosition").asInt()).isEqualTo(2);
    assertThat(second.get("accelSize").asInt()).isEqualTo(3);
    assertThat(second.get("lotPicks").asInt()).isEqualTo(1);
  }

  @Test
  void unpickedLotsStayUnsoldAndOnlyPickedOnesRun() {
    Room room = mainDone(6, 3);
    control(room, "open_round");
    picks(room, room.manager(1), List.of(5));

    control(room, "end_round");

    JsonNode live = view(room);
    assertThat(live.get("lot").get("name").asText()).isEqualTo("Player 5");
    assertThat(numbers(live.get("unsold"))).containsExactly(4, 6);
    advance(20);
    tick();
    advance(5);
    tick();
    assertThat(phase(room)).isEqualTo("ended");
    assertThat(numbers(view(room).get("unsold"))).containsExactly(4, 6, 7);
  }

  // ---- T-13: the guards still apply ----

  @Test
  void aWonAcceleratedLotStillFailsTheEligibilityGuardWhenTheSquadIsFull() {
    Room room = mainDone(7, 5);
    // Manager 0 holds 5 of 5 places, so it has no open slots and cannot bid in the round.
    control(room, "open_round");
    assertThat(outcome(picks(room, room.manager(0), List.of(6)))).isEqualTo("rejected:over_slots");
    picks(room, room.manager(1), List.of(6, 7));
    control(room, "end_round");

    JsonNode lot = view(room).get("lot");
    assertThat(lot.get("no").asInt()).isEqualTo(8);
    assertThat(outcome(bid(room, room.manager(0), 8, 20))).isEqualTo("rejected:squad_full");
    assertThat(outcome(bid(room, room.manager(1), 8, 20))).isEqualTo("accepted");
    advance(10);
    tick();
    JsonNode after = view(room);
    assertThat(after.get("lastResult").get("sold").asBoolean()).isTrue();
    assertThat(after.get("lastResult").get("franchiseId").asText())
        .isEqualTo(room.franchise(1).toString());
  }
}
