package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 10-squads AC-6: the squad view reads the auction state's standings, so every room member sees
 * every franchise's squad and purse, before the first hammer and after each one, and a non-member
 * gets the existing refusal. No endpoint or schema is added for it.
 */
class AuctionSquadsIT extends AbstractAuctionIT {

  /**
   * Before go-live the engine has no standings yet, so the state names the start and no franchise;
   * the squad view then takes the franchises and the full purse from the room (AC-4).
   */
  @Test
  void beforeGoLiveEveryMemberReadsTheStartAndNoSquadsAndTheRoomHoldsTheFranchisesAndPurse() {
    Room room = room(3, 6);
    assertThat(schedule(room, T0.plusSeconds(3600)).getStatusCode()).isEqualTo(HttpStatus.OK);

    for (Person who :
        List.of(room.manager(0), room.manager(1), room.manager(2), room.commissioner())) {
      JsonNode s = state(room, who);
      assertThat(s.get("phase").asText()).isEqualTo("scheduled");
      assertThat(s.get("standings")).isEmpty();
      assertThat(s.get("lotsClosed").asInt()).isZero();
      assertThat(s.get("startsAt").asText()).isEqualTo(T0.plusSeconds(3600).toString());
      JsonNode r = body(get(who, "/api/rooms/" + room.id()));
      assertThat(r.get("config").get("purse").asLong()).isEqualTo(1000);
      assertThat(
              body(get(who, "/api/rooms/" + room.id() + "/members"))
                  .findValues("franchise")
                  .stream()
                  .filter(JsonNode::isObject))
          .hasSize(3);
    }
  }

  @Test
  void aHammerPutsThePlayerAndPriceInTheBuyersSquadForEveryMemberToRead() {
    Room room = room(2, 6);
    goLive(room);
    JsonNode lot = state(room, room.manager(1)).get("lot");
    assertThat(outcome(bid(room, room.manager(0), 1, 20))).isEqualTo("accepted");
    assertThat(outcome(bid(room, room.manager(1), 1, 25))).isEqualTo("accepted");
    advance(10);
    tick();

    for (Person who : List.of(room.manager(0), room.manager(1), room.commissioner())) {
      JsonNode s = state(room, who);
      JsonNode bought = null;
      JsonNode other = null;
      for (JsonNode st : s.get("standings")) {
        if (st.get("franchiseId").asText().equals(room.franchise(1).toString())) {
          bought = st;
        } else {
          other = st;
        }
      }
      assertThat(bought).isNotNull();
      assertThat(bought.get("purse").asLong()).isEqualTo(975);
      assertThat(bought.get("squad")).hasSize(1);
      assertThat(bought.get("squad").get(0).get("lot").asInt()).isEqualTo(1);
      assertThat(bought.get("squad").get(0).get("playerId").asText())
          .isEqualTo(lot.get("playerId").asText());
      assertThat(bought.get("squad").get(0).get("price").asLong()).isEqualTo(25);
      assertThat(other.get("purse").asLong()).isEqualTo(1000);
      assertThat(other.get("squad")).isEmpty();
      assertThat(s.get("lastResult").get("sold").asBoolean()).isTrue();
      assertThat(s.get("lastResult").get("franchiseId").asText())
          .isEqualTo(room.franchise(1).toString());
    }
  }

  @Test
  void aNonMemberGetsTheExistingNotFoundOnTheStateAndTheLiveTicket() {
    Room room = room(2, 6);
    goLive(room);
    Person outsider = person();

    assertThat(get(outsider, auction(room, "")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(post(outsider, auction(room, "/ticket"), null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(null, auction(room, "")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
