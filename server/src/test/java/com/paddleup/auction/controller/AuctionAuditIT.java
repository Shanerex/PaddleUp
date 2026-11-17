package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-13: the audit log, read-only for every room member. */
class AuctionAuditIT extends AbstractAuctionIT {

  private JsonNode audit(Room room, Person who, String query) {
    var response = get(who, auction(room, "/audit" + query));
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    return body(response);
  }

  private static List<String> kinds(JsonNode page) {
    List<String> out = new ArrayList<>();
    page.get("entries").forEach(e -> out.add(e.get("kind").asText()));
    return out;
  }

  /** A live room with a bid, a pass, a sale, a pause, a resume and a skip, in that order. */
  private Room room() {
    Room room = room(2, 6);
    goLive(room);
    bid(room, room.manager(0), 1, 20);
    pass(room, room.manager(1), 1);
    advance(10);
    tick();
    advance(5);
    tick();
    control(room, "pause");
    control(room, "resume");
    control(room, "skip");
    return room;
  }

  @Test
  void everyMemberSeesBidPassAndAdminEntriesInTimeOrderAndEachFilterReturnsOnlyItsKind() {
    Room room = room();

    for (Person who : List.of(room.manager(0), room.manager(1), room.commissioner())) {
      JsonNode all = audit(room, who, "");
      assertThat(kinds(all))
          .containsExactly(
              "scheduled",
              "auction_started",
              "bid_accepted",
              "passed",
              "lot_sold",
              "paused",
              "resumed",
              "lot_skipped");
      long previous = 0;
      for (JsonNode e : all.get("entries")) {
        assertThat(e.get("seq").asLong()).isGreaterThan(previous);
        previous = e.get("seq").asLong();
        assertThat(e.get("at").asText()).endsWith("Z");
      }
      assertThat(all.get("more").asBoolean()).isFalse();
    }
    assertThat(kinds(audit(room, room.manager(0), "?kind=bids")))
        .containsExactly("bid_accepted", "lot_sold");
    assertThat(kinds(audit(room, room.manager(0), "?kind=passes"))).containsExactly("passed");
    assertThat(kinds(audit(room, room.manager(0), "?kind=admin")))
        .containsExactly("scheduled", "auction_started", "paused", "resumed", "lot_skipped");
    assertThat(kinds(audit(room, room.manager(0), "?kind=lifelines"))).isEmpty();
    assertThat(kinds(audit(room, room.manager(0), "?kind=rtm"))).isEmpty();
    JsonNode bid = audit(room, room.manager(1), "?kind=bids").get("entries").get(0);
    assertThat(bid.get("lot").asInt()).isEqualTo(1);
    assertThat(bid.get("franchiseId").asText()).isEqualTo(room.franchise(0).toString());
    assertThat(bid.get("detail").get("amount").asLong()).isEqualTo(20);
    // No user or intent id is ever in the log a member reads.
    assertThat(audit(room, room.manager(1), "").toString()).doesNotContain("intentId", "actor");
  }

  @Test
  void aLifelineEntryHoldsOnlyItsTypeLotAndTime() {
    Room room = room();
    long seq = lastSeq(room);
    jdbc.update(
        "INSERT INTO auction_events (room_id, seq, occurred_at, kind, franchise_id, lot_no, payload)"
            + " VALUES (?, ?, now(), 'lifeline_spent', ?, 4,"
            + " '{\"type\":\"analyst\",\"advice\":\"Stop at 9\",\"prompt\":\"secret\"}'::jsonb)",
        UUID.fromString(room.id()),
        seq + 1,
        room.franchise(0));

    JsonNode lifelines = audit(room, room.manager(1), "?kind=lifelines");

    assertThat(lifelines.get("entries")).hasSize(1);
    JsonNode entry = lifelines.get("entries").get(0);
    assertThat(entry.get("kind").asText()).isEqualTo("lifeline_spent");
    assertThat(entry.get("lot").asInt()).isEqualTo(4);
    assertThat(entry.get("at").asText()).endsWith("Z");
    assertThat(entry.get("detail").get("type").asText()).isEqualTo("analyst");
    assertThat(entry.has("franchiseId")).isFalse();
    assertThat(entry.toString()).doesNotContain("advice", "prompt", "secret", "Stop at 9");
    assertThat(audit(room, room.manager(1), "").toString()).doesNotContain("secret");
  }

  @Test
  void theReopenReasonIsInTheAuditEntryAndAPageContinuesFromItsLastSeq() {
    Room room = room();
    advance(5);
    tick();
    assertThat(outcome(control(room, "reopen", "lot", 1, "reason", "Wrong paddle")))
        .isEqualTo("accepted");

    JsonNode first = audit(room, room.manager(0), "?limit=3");
    assertThat(first.get("entries")).hasSize(3);
    assertThat(first.get("more").asBoolean()).isTrue();
    JsonNode rest = audit(room, room.manager(0), "?after=" + first.get("lastSeq").asLong());

    JsonNode reopened = null;
    for (JsonNode e : rest.get("entries")) {
      if (e.get("kind").asText().equals("lot_reopened")) {
        reopened = e;
      }
    }
    assertThat(reopened).isNotNull();
    assertThat(reopened.get("detail").get("reason").asText()).isEqualTo("Wrong paddle");
    assertThat(reopened.get("lot").asInt()).isEqualTo(1);
    assertThat(rest.get("more").asBoolean()).isFalse();
  }

  @Test
  void aNonMemberGets404AndABadFilterIs400() {
    Room room = room();

    assertThat(get(person(), auction(room, "/audit")).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(room.manager(0), auction(room, "/audit?kind=scoring")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(get(room.manager(0), auction(room, "/audit?after=-1")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void anAuctionNothingHasHappenedInGivesAnEmptyPage() {
    Room room = room(2, 6);

    JsonNode page = audit(room, room.manager(0), "");

    assertThat(page.get("entries")).isEmpty();
    assertThat(page.get("lastSeq").asLong()).isZero();
    assertThat(page.get("more").asBoolean()).isFalse();
  }
}
