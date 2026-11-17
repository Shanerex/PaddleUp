package com.paddleup.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** AC-2, AC-3, AC-4, AC-6, AC-7, AC-8: Commissioner controls over HTTP. */
class AuctionControlsIT extends AbstractAuctionIT {

  @MockitoSpyBean AuctionBroadcaster broadcaster;

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
}
