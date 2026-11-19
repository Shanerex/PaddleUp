package com.paddleup.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import com.paddleup.auction.repository.AuctionEventRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** AC-2, AC-3, AC-4, AC-6, AC-7, AC-8: Commissioner controls over HTTP. */
class AuctionControlsIT extends AbstractAuctionIT {

  @MockitoSpyBean AuctionBroadcaster broadcaster;
  @MockitoSpyBean AuctionEventRepository eventLog;

  private String phase(Room room) {
    return state(room, room.commissioner()).get("phase").asText();
  }

  // ---- T-4: who may send a control, and what a repeat or a refusal does ----

  @Test
  void aManagersControlIs403WritesNoEventAndBroadcastsNothing() {
    Room room = room(2, 6);
    goLive(room);
    long seq = lastSeq(room);
    int rejections = rejectionCount(room);
    clearInvocations(broadcaster);

    for (String action : new String[] {"pause", "skip", "adjourn", "open_round", "end_auction"}) {
      var response = controlAs(room, room.manager(0), UUID.randomUUID(), action);
      assertThat(response.getStatusCode()).as(action).isEqualTo(HttpStatus.FORBIDDEN);
    }

    assertThat(lastSeq(room)).isEqualTo(seq);
    assertThat(rejectionCount(room)).isEqualTo(rejections);
    verify(broadcaster, never()).events(any(), any(), any());
    assertThat(phase(room)).isEqualTo("live");
  }

  @Test
  void aNonMemberGets404() {
    Room room = room(2, 6);
    goLive(room);

    var response = controlAs(room, person(), UUID.randomUUID(), "pause");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(eventCount(room, "paused")).isZero();
  }

  @Test
  void oneIntentIdSentTwiceAddsOneEventAndGetsTheSameAnswer() {
    Room room = room(2, 6);
    goLive(room);
    UUID intent = UUID.randomUUID();

    var first = control(room, intent, "pause");
    long seq = lastSeq(room);
    var again = control(room, intent, "pause");

    assertThat(outcome(first)).isEqualTo("accepted");
    assertThat(outcome(again)).isEqualTo("accepted");
    assertThat(body(again).get("seq").asLong()).isEqualTo(body(first).get("seq").asLong());
    assertThat(eventCount(room, "paused")).isEqualTo(1);
    assertThat(lastSeq(room)).isEqualTo(seq);
  }

  @Test
  void aRefusalIsStoredSoAResendGetsTheSameAnswerEvenAfterTheStateChanged() {
    Room room = room(2, 6);
    goLive(room);
    UUID intent = UUID.randomUUID();
    long seq = lastSeq(room);

    assertThat(outcome(control(room, intent, "resume"))).isEqualTo("rejected:wrong_phase");
    assertThat(rejectionCount(room)).isEqualTo(1);
    assertThat(outcome(control(room, "pause"))).isEqualTo("accepted");

    // Now paused, where resume would be accepted: the first answer still stands.
    assertThat(outcome(control(room, intent, "resume"))).isEqualTo("rejected:wrong_phase");
    assertThat(rejectionCount(room)).isEqualTo(1);
    assertThat(lastSeq(room)).isEqualTo(seq + 1);
    assertThat(phase(room)).isEqualTo("paused");
  }

  @Test
  void anUnknownActionOrAMissingFieldIs400() {
    Room room = room(2, 6);
    goLive(room);

    assertThat(control(room, "levitate").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    var noIntent =
        post(room.commissioner(), auction(room, "/controls"), java.util.Map.of("action", "pause"));
    assertThat(noIntent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode stateNow = state(room, room.commissioner());
    assertThat(stateNow.get("phase").asText()).isEqualTo("live");
  }

  // ---- T-5: pause and resume ----

  @Test
  void pauseFreezesTheTimerAndRefusesBidsAndPassesAndResumeRestoresTheTime() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    advance(6);
    JsonNode before = state(room, room.commissioner());
    assertThat(before.get("deadline").asText()).isEqualTo(T0.plusSeconds(10).toString());

    assertThat(outcome(control(room, "pause"))).isEqualTo("accepted");
    JsonNode paused = state(room, room.commissioner());
    assertThat(paused.get("phase").asText()).isEqualTo("paused");
    assertThat(paused.get("heldMs").asLong()).isEqualTo(4000);
    assertThat(paused.get("lotOpen").asBoolean()).isTrue();
    assertThat(paused.has("deadline")).isFalse();

    // A long time passes: nothing expires, nothing is accepted.
    advance(3600);
    tick();
    assertThat(eventCount(room, "lot_sold")).isZero();
    assertThat(state(room, room.commissioner()).get("phase").asText()).isEqualTo("paused");
    var late = bid(room, room.manager(1), 1, 25);
    assertThat(late.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(late).get("error").asText()).isEqualTo("paused");
    assertThat(pass(room, room.manager(1), 1).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(eventCount(room, "bid_accepted")).isEqualTo(1);

    assertThat(outcome(control(room, "resume"))).isEqualTo("accepted");
    JsonNode live = state(room, room.commissioner());
    assertThat(live.get("phase").asText()).isEqualTo("live");
    assertThat(live.get("heldMs").asLong()).isZero();
    assertThat(java.time.Instant.parse(live.get("deadline").asText()))
        .isEqualTo(clock.instant().plusMillis(4000));
    advance(3);
    tick();
    assertThat(eventCount(room, "lot_sold")).isZero();
    advance(1);
    tick();
    assertThat(eventCount(room, "lot_sold")).isEqualTo(1);
  }

  @Test
  void pausingInTheGapHoldsTheRestOfTheGap() {
    Room room = room(2, 6);
    goLive(room);
    advance(20);
    tick();
    advance(2);
    assertThat(outcome(control(room, "pause"))).isEqualTo("accepted");
    JsonNode paused = state(room, room.commissioner());
    assertThat(paused.get("heldMs").asLong()).isEqualTo(3000);
    assertThat(paused.get("lotOpen").asBoolean()).isFalse();

    advance(600);
    tick();
    assertThat(eventCount(room, "lot_opened")).isEqualTo(1);
    assertThat(outcome(control(room, "resume"))).isEqualTo("accepted");
    advance(2);
    tick();
    assertThat(eventCount(room, "lot_opened")).isEqualTo(1);
    advance(1);
    tick();
    assertThat(eventCount(room, "lot_opened")).isEqualTo(2);
  }

  /**
   * CR3-2: once start_now is stored, a go-live that fails to write is not a failed control: the
   * answer stays accepted for every resend and the next tick goes live.
   */
  @Test
  void aGoLiveThatFailsAfterStartNowStillAnswersAcceptedAndTheTickRetriesIt() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(3600));
    schedule(room, T0);
    UUID intent = UUID.randomUUID();
    // start_now is written; the first write of the go-live (auction_started) fails.
    org.mockito.Mockito.doCallRealMethod()
        .doThrow(new DataAccessResourceFailureException("disk gone"))
        .doCallRealMethod()
        .when(eventLog)
        .append(any(), any());

    var first = control(room, intent, "start_now");
    org.mockito.Mockito.reset(eventLog);

    assertThat(outcome(first)).isEqualTo("accepted");
    assertThat(eventCount(room, "start_now")).isEqualTo(1);
    assertThat(eventCount(room, "auction_started")).isZero();
    assertThat(phase(room)).isEqualTo("scheduled");
    assertThat(outcome(control(room, intent, "start_now"))).isEqualTo("accepted");
    assertThat(eventCount(room, "start_now")).isEqualTo(1);

    tick();

    assertThat(phase(room)).isEqualTo("live");
    assertThat(eventCount(room, "auction_started")).isEqualTo(1);
  }

  @Test
  void pauseAndResumeAreRefusedOutsideTheirPhase() {
    Room room = room(2, 6);
    goLive(room);

    assertThat(outcome(control(room, "resume"))).isEqualTo("rejected:wrong_phase");
    assertThat(outcome(control(room, "pause"))).isEqualTo("accepted");
    assertThat(outcome(control(room, "pause"))).isEqualTo("rejected:wrong_phase");
    assertThat(outcome(control(room, "skip"))).isEqualTo("rejected:wrong_phase");
    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "x")))
        .isEqualTo("rejected:wrong_phase");
    assertThat(eventCount(room, "paused")).isEqualTo(1);
  }

  // ---- T-6: skip ----

  @Test
  void skippingALotWithNoBidClosesItUnsoldAndLogsIt() {
    Room room = room(2, 6);
    goLive(room);
    pass(room, room.manager(0), 1);

    assertThat(outcome(control(room, "skip", "lot", 1))).isEqualTo("accepted");

    JsonNode after = state(room, room.commissioner());
    assertThat(after.get("lotOpen").asBoolean()).isFalse();
    assertThat(after.get("unsold")).hasSize(1);
    assertThat(after.get("lastResult").get("sold").asBoolean()).isFalse();
    assertThat(eventCount(room, "lot_skipped")).isEqualTo(1);
    assertThat(eventCount(room, "lot_unsold")).isZero();
    advance(5);
    tick();
    assertThat(state(room, room.commissioner()).get("lot").get("no").asInt()).isEqualTo(2);
  }

  @Test
  void skippingALotWithABidIsRefusedWithAReasonAndWritesNoEvent() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    long seq = lastSeq(room);

    assertThat(outcome(control(room, "skip"))).isEqualTo("rejected:has_bid");

    assertThat(lastSeq(room)).isEqualTo(seq);
    assertThat(eventCount(room, "lot_skipped")).isZero();
    assertThat(state(room, room.commissioner()).get("lotOpen").asBoolean()).isTrue();
  }

  @Test
  void skipNeedsAnOpenLotAndTheLotItNames() {
    Room room = room(2, 6);
    goLive(room);

    assertThat(outcome(control(room, "skip", "lot", 3))).isEqualTo("rejected:lot_closed");
    advance(20);
    tick();
    assertThat(outcome(control(room, "skip"))).isEqualTo("rejected:no_open_lot");
    assertThat(eventCount(room, "lot_skipped")).isZero();
  }

  // ---- T-7: re-open ----

  @Test
  void reopenWithoutAReasonIsRejectedAndTheReasonIsCleanedAndCapped() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    advance(10);
    tick();

    assertThat(outcome(control(room, "reopen", "lot", 1))).isEqualTo("rejected:reason_required");
    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "   <i></i> ")))
        .isEqualTo("rejected:reason_required");
    // The cap is 140 code points, as the form's; over-long input is too long, never missing.
    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "x".repeat(141))))
        .isEqualTo("rejected:reason_too_long");
    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "x".repeat(501))))
        .isEqualTo("rejected:reason_too_long");
    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "x".repeat(4001))))
        .isEqualTo("rejected:reason_too_long");
    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "x".repeat(9000))))
        .isEqualTo("rejected:reason_too_long");
    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "\uD83C\uDFD3".repeat(141))))
        .isEqualTo("rejected:reason_too_long");
    assertThat(eventCount(room, "lot_reopened")).isZero();

    assertThat(
            outcome(
                control(
                    room, "reopen", "lot", 1, "reason", "<b>Wrong</b>  paddle <script>x</script>")))
        .isEqualTo("accepted");
    String reason =
        jdbc.queryForObject(
            "SELECT payload->>'reason' FROM auction_events WHERE room_id = ?"
                + " AND kind = 'lot_reopened'",
            String.class,
            UUID.fromString(room.id()));
    assertThat(reason).isEqualTo("Wrong paddle");
  }

  @Test
  void reopeningASoldLotReversesPurseAndSquadAndReplaysItNextFromBase() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    bid(room, room.manager(1), 1, 25);
    advance(10);
    tick();
    advance(5);
    tick();
    // Lot 2 is open; lot 1 was sold to franchise 1 at 25.
    assertThat(standing(room, 1).get("purse").asLong()).isEqualTo(975);

    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "Bid on the wrong player")))
        .isEqualTo("accepted");

    assertThat(standing(room, 1).get("purse").asLong()).isEqualTo(1000);
    assertThat(standing(room, 1).get("squad")).isEmpty();
    JsonNode reopened = event(room, "lot_reopened");
    assertThat(reopened.get("payload").get("reason").asText()).isEqualTo("Bid on the wrong player");
    assertThat(reopened.get("lot").asInt()).isEqualTo(1);
    assertThat(reopened.get("payload").get("newLot").asInt()).isEqualTo(7);
    // The current lot carries on; the re-opened one is next, from base, with no earlier bids.
    assertThat(state(room, room.commissioner()).get("lot").get("no").asInt()).isEqualTo(2);
    advance(20);
    tick();
    JsonNode gap = state(room, room.commissioner());
    assertThat(gap.get("nextLot").get("no").asInt()).isEqualTo(7);
    assertThat(gap.get("nextLot").get("name").asText()).isEqualTo("Player 1");
    advance(5);
    tick();
    JsonNode again = state(room, room.commissioner());
    assertThat(again.get("lot").get("no").asInt()).isEqualTo(7);
    assertThat(again.get("currentBid").asLong()).isZero();
    assertThat(again.has("leaderFranchiseId")).isFalse();
    assertThat(again.get("nextBid").asLong()).isEqualTo(20);
    assertThat(outcome(bid(room, room.manager(0), 7, 20))).isEqualTo("accepted");
  }

  @Test
  void reopeningAnUnsoldLotRemovesItFromTheUnsoldList() {
    Room room = room(2, 6);
    goLive(room);
    advance(20);
    tick();
    assertThat(state(room, room.commissioner()).get("unsold")).hasSize(1);

    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "Nobody was ready")))
        .isEqualTo("accepted");

    assertThat(state(room, room.commissioner()).get("unsold")).isEmpty();
  }

  @Test
  void aLotThatIsOpenQueuedOrAlreadyReopenedCannotBeReopened() {
    Room room = room(2, 6);
    goLive(room);

    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "r")))
        .isEqualTo("rejected:not_closed");
    assertThat(outcome(control(room, "reopen", "lot", 4, "reason", "r")))
        .isEqualTo("rejected:not_closed");
    assertThat(outcome(control(room, "reopen", "lot", 99, "reason", "r")))
        .isEqualTo("rejected:not_closed");
    advance(20);
    tick();
    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "first"))).isEqualTo("accepted");
    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "again")))
        .isEqualTo("rejected:superseded");
    assertThat(eventCount(room, "lot_reopened")).isEqualTo(1);
  }

  @Test
  void theLastLotCanBeReopenedDuringItsGapAndThePhaseChangesOnlyAfterTheGap() {
    Room room = room(2, 2);
    goLive(room);
    for (int lot = 1; lot <= 2; lot++) {
      bid(room, room.manager(0), lot, 20);
      advance(10);
      tick();
      if (lot == 1) {
        advance(5);
        tick();
      }
    }
    // Lot 2 sold, gap running: still live, and re-opening is allowed.
    assertThat(phase(room)).isEqualTo("live");
    advance(2);
    tick();
    assertThat(outcome(control(room, "reopen", "lot", 2, "reason", "Wrong last lot")))
        .isEqualTo("accepted");
    advance(3);
    tick();
    JsonNode open = state(room, room.commissioner());
    assertThat(open.get("phase").asText()).isEqualTo("live");
    assertThat(open.get("lot").get("no").asInt()).isEqualTo(3);
    assertThat(open.get("lotOpen").asBoolean()).isTrue();
    // Nobody bids: lot 3 closes unsold, its gap runs, then the pool is done with lot 3 in it.
    advance(20);
    tick();
    assertThat(phase(room)).isEqualTo("live");
    advance(5);
    tick();
    assertThat(phase(room)).isEqualTo("main_done");
    assertThat(state(room, room.commissioner()).get("unsold").get(0).asInt()).isEqualTo(3);
  }

  @Test
  void withAGapOfZeroThereIsNoWindowToReopenTheLastLot() {
    Room room = room(2, 2, 20, 10, 0);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    advance(10);
    tick();
    bid(room, room.manager(0), 2, 20);
    advance(10);
    tick();

    assertThat(phase(room)).isEqualTo("ended");
    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "Too late")))
        .isEqualTo("rejected:wrong_phase");
  }

  private JsonNode event(Room room, String kind) {
    for (JsonNode e : events(room, room.commissioner(), 0).get("events")) {
      if (e.get("kind").asText().equals(kind)) {
        return e;
      }
    }
    throw new AssertionError("no " + kind + " event");
  }

  private JsonNode standing(Room room, int franchise) {
    for (JsonNode standing : state(room, room.commissioner()).get("standings")) {
      if (standing.get("franchiseId").asText().equals(room.franchise(franchise).toString())) {
        return standing;
      }
    }
    throw new AssertionError("no standing");
  }

  // ---- T-15: persisted before broadcast, nothing broadcast on a failed write ----

  @Test
  void everyControlIsInTheLogWhenItIsBroadcast() {
    Room room = room(2, 4);
    goLive(room);
    UUID roomId = UUID.fromString(room.id());
    List<String> problems = new ArrayList<>();
    List<String> seen = new ArrayList<>();
    doAnswer(
            call -> {
              List<com.paddleup.auction.model.AuctionEvent> sent = call.getArgument(1);
              long stored = lastSeq(room);
              for (var e : sent) {
                seen.add(e.kind().value());
                if (e.seq() > stored) {
                  problems.add(e.kind().value() + " broadcast before it was stored");
                }
              }
              return call.callRealMethod();
            })
        .when(broadcaster)
        .events(any(), any(), any());

    assertThat(roomId).isNotNull();
    control(room, "pause");
    control(room, "resume");
    control(room, "skip");
    control(room, "reopen", "lot", 1, "reason", "Skipped by mistake");
    control(room, "adjourn", "resumeAt", clock.instant().plusSeconds(3600).toString());
    control(room, "change_time", "resumeAt", clock.instant().plusSeconds(7200).toString());
    control(room, "resume_now");
    control(room, "start_now");
    org.mockito.Mockito.reset(broadcaster);

    assertThat(problems).isEmpty();
    assertThat(seen)
        .containsExactly(
            "paused",
            "resumed",
            "lot_skipped",
            "lot_reopened",
            "adjourned",
            "resume_time_changed",
            "resume_lobby_opened",
            "start_now",
            "resumed");
  }

  @Test
  void aForcedLogWriteFailureOnAControlBroadcastsNothingAndLeavesStateUnchanged() {
    Room room = room(2, 4);
    goLive(room);
    Object[][] atOpenLot = {
      {"pause"}, {"skip"}, {"adjourn", "resumeAt", clock.instant().plusSeconds(3600).toString()}
    };
    for (Object[] c : atOpenLot) {
      assertFailsCleanly(room, (String) c[0], java.util.Arrays.copyOfRange(c, 1, c.length));
    }
    advance(20);
    tick();
    assertFailsCleanly(room, "reopen", new Object[] {"lot", 1, "reason", "Nobody was ready"});
    // Nothing was lost: the same controls work once the log is back.
    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "Nobody was ready")))
        .isEqualTo("accepted");
  }

  private void assertFailsCleanly(Room room, String action, Object[] extra) {
    long seq = lastSeq(room);
    JsonNode before = withoutNow(state(room, room.commissioner()));
    clearInvocations(broadcaster);
    doThrow(new DataAccessResourceFailureException("disk gone"))
        .when(eventLog)
        .append(any(), any());
    var failed = control(room, action, extra);
    org.mockito.Mockito.reset(eventLog);

    assertThat(failed.getStatusCode()).as(action).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(body(failed).get("error").asText()).isEqualTo("log_unavailable");
    verify(broadcaster, never()).events(any(), any(), any());
    assertThat(lastSeq(room)).as(action).isEqualTo(seq);
    assertThat(withoutNow(state(room, room.commissioner()))).as(action).isEqualTo(before);
  }

  // ---- fix loop 1, CR-1: a schedule change is only for the lobby ----

  private void assertScheduleRefused(Room room, String phase) {
    long seq = lastSeq(room);
    var response = schedule(room, clock.instant().plusSeconds(3600));

    assertThat(response.getStatusCode()).as(phase).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(response).get("error").asText()).as(phase).isEqualTo("locked");
    assertThat(lastSeq(room)).as(phase).isEqualTo(seq);
    assertThat(eventCount(room, "rescheduled")).as(phase).isZero();
    assertThat(phase(room)).isEqualTo(phase);
  }

  @Test
  void aScheduleChangeIsRefusedInEveryPhaseAfterGoLive() {
    Room room = room(2, 2);
    goLive(room);
    control(room, "pause");
    assertScheduleRefused(room, "paused");

    control(room, "adjourn", "resumeAt", clock.instant().plusSeconds(3600).toString());
    assertScheduleRefused(room, "adjourned");

    control(room, "resume_now");
    assertScheduleRefused(room, "resume_lobby");

    control(room, "start_now");
    for (int lot = 1; lot <= 2; lot++) {
      advance(20);
      tick();
      advance(5);
      tick();
    }
    assertScheduleRefused(room, "main_done");

    control(room, "open_round");
    assertScheduleRefused(room, "nominating");
  }

  private static JsonNode withoutNow(JsonNode state) {
    var copy = (com.fasterxml.jackson.databind.node.ObjectNode) state.deepCopy();
    copy.remove("serverNow");
    return copy;
  }
}
