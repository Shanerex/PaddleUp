package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/** AC-4 to AC-8, AC-10: bids and passes over REST. */
class AuctionBidIT extends AbstractAuctionIT {

  @Test
  void onlyTheLadderAmountsAreAcceptedAndRejectionsWriteNoEvent() {
    Room room = room(3, 6);
    goLive(room);
    long seq = lastSeq(room);

    assertThat(outcome(bid(room, room.manager(0), 1, 25))).isEqualTo("rejected:outbid");
    assertThat(outcome(bid(room, room.manager(0), 1, 15))).isEqualTo("rejected:outbid");
    assertThat(lastSeq(room)).isEqualTo(seq);
    assertThat(outcome(bid(room, room.manager(0), 1, 20))).isEqualTo("accepted");
    assertThat(outcome(bid(room, room.manager(1), 1, 20))).isEqualTo("rejected:outbid");
    assertThat(outcome(bid(room, room.manager(1), 1, 30))).isEqualTo("rejected:outbid");
    assertThat(outcome(bid(room, room.manager(1), 1, 25))).isEqualTo("accepted");
    assertThat(lastSeq(room)).isEqualTo(seq + 2);
  }

  @Test
  void theLeaderANonFranchiseAndAFranchiseShortOfPurseAreRejectedWithAReason() {
    Room room = room(2, 6);
    replacePool(room.id(), 6, 1500);
    goLive(room);
    long seq = lastSeq(room);

    // Base price 1500 is above every purse of 1000.
    assertThat(outcome(bid(room, room.manager(0), 1, 1500))).isEqualTo("rejected:purse");
    assertThat(outcome(bid(room, room.commissioner(), 1, 1500)))
        .isEqualTo("rejected:not_a_franchise");
    assertThat(lastSeq(room)).isEqualTo(seq);
  }

  @Test
  void theLeaderCannotBidAgainAndANonMemberGets404() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    long seq = lastSeq(room);

    assertThat(outcome(bid(room, room.manager(0), 1, 25))).isEqualTo("rejected:leader");
    assertThat(bid(room, person(), 1, 25).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(lastSeq(room)).isEqualTo(seq);
  }

  @Test
  void aPassIsLoggedBarsOnlyThatLotAndMovesNothingElse() {
    Room room = room(2, 6);
    goLive(room);
    JsonNode before = state(room, room.manager(0));

    assertThat(outcome(pass(room, room.manager(1), 1))).isEqualTo("accepted");

    JsonNode after = state(room, room.manager(0));
    assertThat(after.get("deadline")).isEqualTo(before.get("deadline"));
    assertThat(after.get("lot").get("no").asInt()).isEqualTo(1);
    assertThat(after.get("lotOpen").asBoolean()).isTrue();
    assertThat(after.get("passedBy").get(0).asText()).isEqualTo(room.franchise(1).toString());
    assertThat(
            events(room, room.manager(0), before.get("lastSeq").asLong())
                .get("events")
                .get(0)
                .get("kind")
                .asText())
        .isEqualTo("passed");
    assertThat(outcome(bid(room, room.manager(1), 1, 20))).isEqualTo("rejected:passed");
    assertThat(outcome(bid(room, room.manager(0), 1, 20))).isEqualTo("accepted");

    advance(10);
    tick();
    advance(5);
    tick();
    assertThat(state(room, room.manager(0)).get("lot").get("no").asInt()).isEqualTo(2);
    assertThat(outcome(bid(room, room.manager(1), 2, 20))).isEqualTo("accepted");
  }

  @Test
  void anAcceptedBidMovesTheDeadlineToServerNowPlusTBidWhateverTheClientClaims() {
    Room room = room(2, 6);
    goLive(room);
    advance(4);
    Instant now = clock.instant();

    var headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, room.manager(0).cookie());
    headers.add(HttpHeaders.DATE, "Mon, 01 Jan 2035 00:00:00 GMT");
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    var skewed =
        rest.exchange(
            auction(room, "/bids"),
            HttpMethod.POST,
            new org.springframework.http.HttpEntity<>(
                Map.of(
                    "intentId", UUID.randomUUID().toString(),
                    "lot", 1,
                    "amount", 20,
                    "clientTime", now.plusSeconds(3600).toString(),
                    "deadline", now.plusSeconds(3600).toString()),
                headers),
            String.class);

    assertThat(outcome(skewed)).isEqualTo("accepted");
    assertThat(Instant.parse(body(skewed).get("deadline").asText())).isEqualTo(now.plusSeconds(10));
    assertThat(Instant.parse(body(skewed).get("serverNow").asText())).isEqualTo(now);
    assertThat(Instant.parse(state(room, room.manager(1)).get("deadline").asText()))
        .isEqualTo(now.plusSeconds(10));
    // Another franchise's later outcomes are decided only by the server clock.
    advance(11);
    assertThat(outcome(bid(room, room.manager(1), 1, 25))).isEqualTo("rejected:lot_closed");
  }

  @Test
  void aBidAfterTheDeadlineGetsLotClosedEvenBeforeTheTickRuns() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);

    advance(10);
    var late = bid(room, room.manager(1), 1, 25);

    assertThat(outcome(late)).isEqualTo("rejected:lot_closed");
    assertThat(eventCount(room, "lot_sold")).isEqualTo(1);
    assertThat(eventCount(room, "bid_accepted")).isEqualTo(1);
  }

  @Test
  void aBidJustBeforeTheDeadlineCounts() {
    Room room = room(2, 6);
    goLive(room);
    advance(19);

    assertThat(outcome(bid(room, room.manager(0), 1, 20))).isEqualTo("accepted");
    advance(9);
    tick();
    assertThat(eventCount(room, "lot_sold")).isZero();
    advance(1);
    tick();
    assertThat(eventCount(room, "lot_sold")).isEqualTo(1);
  }

  @Test
  void phaseErrorsAre409WithTheMatrixCodes() {
    Room room = room(2, 3);
    clock.freeze(T0.minusSeconds(60));
    var none = bid(room, room.manager(0), 1, 20);
    assertThat(none.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(none).get("error").asText()).isEqualTo("not_live");
    schedule(room, T0);
    assertThat(body(bid(room, room.manager(0), 1, 20)).get("error").asText()).isEqualTo("not_live");
    assertThat(body(pass(room, room.manager(0), 1)).get("error").asText()).isEqualTo("not_live");

    clock.freeze(T0);
    tick();
    advance(20);
    tick();
    var gap = bid(room, room.manager(0), 1, 20);
    assertThat(gap.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(gap).get("error").asText()).isEqualTo("no_open_lot");
    assertThat(body(pass(room, room.manager(0), 1)).get("error").asText()).isEqualTo("no_open_lot");

    for (int i = 0; i < 2; i++) {
      advance(5);
      tick();
      advance(20);
      tick();
    }
    var ended = bid(room, room.manager(0), 3, 20);
    assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(ended).get("error").asText()).isEqualTo("ended");
  }

  @Test
  void badBodiesAre400() {
    Room room = room(2, 6);
    goLive(room);

    assertThat(
            post(room.manager(0), auction(room, "/bids"), Map.of("lot", 1, "amount", 20))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            post(
                    room.manager(0),
                    auction(room, "/bids"),
                    Map.of("intentId", UUID.randomUUID().toString(), "lot", 0, "amount", 20))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(post(room.manager(0), auction(room, "/passes"), Map.of("lot", 1)).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void noCancelOrUndoRouteExists() {
    Room room = room(2, 6);
    goLive(room);
    UUID intent = UUID.randomUUID();
    bid(room, room.manager(0), intent, 1, 20);
    long seq = lastSeq(room);

    for (String path :
        new String[] {
          "/bids", "/bids/" + intent, "/bids/cancel", "/bids/undo", "/passes/" + intent
        }) {
      for (HttpMethod method :
          new HttpMethod[] {HttpMethod.DELETE, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.POST}) {
        var response =
            call(
                method,
                auction(room, path),
                room.manager(0).cookie(),
                Map.of("intentId", intent.toString()));
        assertThat(response.getStatusCode().is2xxSuccessful()).as(method + " " + path).isFalse();
      }
    }
    assertThat(lastSeq(room)).isEqualTo(seq);
    assertThat(state(room, room.manager(0)).get("currentBid").asLong()).isEqualTo(20);
  }
}
