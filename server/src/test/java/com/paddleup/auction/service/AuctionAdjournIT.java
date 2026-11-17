package com.paddleup.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-2, AC-9: adjourn, the resume lobby, Change time and Start now. */
class AuctionAdjournIT extends AbstractAuctionIT {

  private JsonNode view(Room room) {
    return state(room, room.commissioner());
  }

  private String phase(Room room) {
    return view(room).get("phase").asText();
  }

  private Instant in(long seconds) {
    return clock.instant().plusSeconds(seconds);
  }

  // ---- T-8: adjourn, change time, resume ----

  @Test
  void adjournKeepsTheLotItsBidsAndItsRemainingTimeAndLocksBidding() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    advance(6);
    Instant resumeAt = in(86_400);

    assertThat(outcome(control(room, "adjourn", "resumeAt", resumeAt.toString())))
        .isEqualTo("accepted");

    JsonNode adjourned = view(room);
    assertThat(adjourned.get("phase").asText()).isEqualTo("adjourned");
    assertThat(Instant.parse(adjourned.get("resumeAt").asText())).isEqualTo(resumeAt);
    assertThat(adjourned.get("heldMs").asLong()).isEqualTo(4000);
    assertThat(adjourned.get("lot").get("no").asInt()).isEqualTo(1);
    assertThat(adjourned.get("currentBid").asLong()).isEqualTo(20);
    assertThat(adjourned.get("leaderFranchiseId").asText()).isEqualTo(room.franchise(0).toString());
    var refused = bid(room, room.manager(1), 1, 25);
    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(refused).get("error").asText()).isEqualTo("adjourned");
    assertThat(pass(room, room.manager(1), 1).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void adjournNeedsAFutureResumeTimeAndAPausedAuctionKeepsItsHeldTime() {
    Room room = room(2, 6);
    goLive(room);
    advance(5);

    assertThat(outcome(control(room, "adjourn"))).isEqualTo("rejected:time_invalid");
    assertThat(outcome(control(room, "adjourn", "resumeAt", clock.instant().toString())))
        .isEqualTo("rejected:time_invalid");
    assertThat(outcome(control(room, "adjourn", "resumeAt", in(-60).toString())))
        .isEqualTo("rejected:time_invalid");
    assertThat(eventCount(room, "adjourned")).isZero();

    assertThat(outcome(control(room, "pause"))).isEqualTo("accepted");
    advance(1000);
    assertThat(outcome(control(room, "adjourn", "resumeAt", in(60).toString())))
        .isEqualTo("accepted");
    assertThat(view(room).get("heldMs").asLong()).isEqualTo(15_000);
  }

  @Test
  void theResumeTimeOpensTheLobbyNeverTheLotAndTheLobbyThenResumesTheHeldTime() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    advance(6);
    control(room, "adjourn", "resumeAt", in(3600).toString());
    advance(3599);
    tick();
    assertThat(phase(room)).isEqualTo("adjourned");

    // At the stored time only the lobby opens, however late the tick is.
    advance(86_400);
    tick();
    JsonNode lobby = view(room);
    assertThat(lobby.get("phase").asText()).isEqualTo("resume_lobby");
    assertThat(Instant.parse(lobby.get("liveAt").asText())).isEqualTo(in(120));
    var refused = bid(room, room.manager(1), 1, 25);
    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(refused).get("error").asText()).isEqualTo("resume_lobby");
    advance(119);
    tick();
    assertThat(phase(room)).isEqualTo("resume_lobby");

    advance(1);
    tick();
    JsonNode live = view(room);
    assertThat(live.get("phase").asText()).isEqualTo("live");
    assertThat(Instant.parse(live.get("deadline").asText())).isEqualTo(in(4));
    assertThat(live.get("currentBid").asLong()).isEqualTo(20);
    assertThat(outcome(bid(room, room.manager(1), 1, 25))).isEqualTo("accepted");
    assertThat(eventCount(room, "resume_lobby_opened")).isEqualTo(1);
    assertThat(eventCount(room, "resumed")).isEqualTo(1);
  }

  @Test
  void resumeNowOpensTheTwoMinuteLobbyAtOnce() {
    Room room = room(2, 6);
    goLive(room);
    control(room, "adjourn", "resumeAt", in(86_400).toString());
    assertThat(view(room).get("resumeLobbySeconds").asLong()).isEqualTo(120);

    assertThat(outcome(control(room, "resume_now"))).isEqualTo("accepted");

    JsonNode lobby = view(room);
    assertThat(lobby.get("phase").asText()).isEqualTo("resume_lobby");
    assertThat(Instant.parse(lobby.get("liveAt").asText()))
        .isEqualTo(
            clock.instant().plus(Duration.ofSeconds(lobby.get("resumeLobbySeconds").asLong())))
        .isEqualTo(clock.instant().plus(Duration.ofMinutes(2)));
    assertThat(outcome(control(room, "resume_now"))).isEqualTo("rejected:wrong_phase");
  }

  @Test
  void changeTimeEditsTheStoredTimeAndInTheLobbyGoesBackToAdjourned() {
    Room room = room(2, 6);
    goLive(room);
    control(room, "adjourn", "resumeAt", in(3600).toString());

    assertThat(outcome(control(room, "change_time", "resumeAt", in(7200).toString())))
        .isEqualTo("accepted");
    assertThat(Instant.parse(view(room).get("resumeAt").asText())).isEqualTo(in(7200));
    assertThat(outcome(control(room, "change_time", "resumeAt", in(-5).toString())))
        .isEqualTo("rejected:time_invalid");
    advance(7200);
    tick();
    assertThat(phase(room)).isEqualTo("resume_lobby");

    assertThat(outcome(control(room, "change_time", "resumeAt", in(1800).toString())))
        .isEqualTo("accepted");
    JsonNode back = view(room);
    assertThat(back.get("phase").asText()).isEqualTo("adjourned");
    assertThat(Instant.parse(back.get("resumeAt").asText())).isEqualTo(in(1800));
    assertThat(back.has("liveAt")).isFalse();
    advance(1800);
    tick();
    assertThat(phase(room)).isEqualTo("resume_lobby");
    assertThat(eventCount(room, "resume_time_changed")).isEqualTo(2);
  }

  // ---- T-9: Start now ----

  @Test
  void startNowInTheLobbyLogsStartNowThenGoesLive() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(3600));
    schedule(room, T0);

    assertThat(outcome(control(room, "start_now"))).isEqualTo("accepted");

    JsonNode live = view(room);
    assertThat(live.get("phase").asText()).isEqualTo("live");
    assertThat(live.get("lot").get("no").asInt()).isEqualTo(1);
    long startNow = seqOf(room, "start_now");
    assertThat(startNow).isLessThan(seqOf(room, "auction_started"));
    assertThat(outcome(control(room, "start_now"))).isEqualTo("rejected:wrong_phase");
  }

  @Test
  void aRefusedGoLiveAfterStartNowStillLogsStartRefused() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(3600));
    schedule(room, T0);
    replacePool(room.id(), 1, 20);

    assertThat(outcome(control(room, "start_now"))).isEqualTo("accepted");

    assertThat(phase(room)).isEqualTo("scheduled");
    assertThat(eventCount(room, "start_now")).isEqualTo(1);
    assertThat(eventCount(room, "start_refused")).isEqualTo(1);
    assertThat(seqOf(room, "start_now")).isLessThan(seqOf(room, "start_refused"));
    assertThat(view(room).get("startRefusal").asText()).contains("1 player");
  }

  @Test
  void startNowInTheResumeLobbyGoesLiveAtOnceWithTheHeldTime() {
    Room room = room(2, 6);
    goLive(room);
    advance(8);
    control(room, "adjourn", "resumeAt", in(3600).toString());
    control(room, "resume_now");

    assertThat(outcome(control(room, "start_now"))).isEqualTo("accepted");

    JsonNode live = view(room);
    assertThat(live.get("phase").asText()).isEqualTo("live");
    assertThat(Instant.parse(live.get("deadline").asText())).isEqualTo(in(12));
    assertThat(seqOf(room, "start_now")).isLessThan(seqOf(room, "resumed"));
  }

  @Test
  void aManagerCannotStartNow() {
    Room room = room(2, 6);
    clock.freeze(T0.minusSeconds(3600));
    schedule(room, T0);

    var response = controlAs(room, room.manager(0), UUID.randomUUID(), "start_now");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(eventCount(room, "start_now")).isZero();
    assertThat(phase(room)).isEqualTo("scheduled");
  }

  private long seqOf(Room room, String kind) {
    return jdbc.queryForObject(
        "SELECT min(seq) FROM auction_events WHERE room_id = ? AND kind = ?",
        Long.class,
        UUID.fromString(room.id()),
        kind);
  }
}
