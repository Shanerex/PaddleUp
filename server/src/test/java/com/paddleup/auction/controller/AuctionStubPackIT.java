package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.auction.AbstractAuctionIT;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

/**
 * AC-16: the stub football pack and a 20-player fake pool run a scripted auction to the end, with
 * sold and unsold lots, and the engine needs no change for the second sport.
 */
@TestPropertySource(properties = "paddleup.packs.dir=src/test/resources/packs/stub")
class AuctionStubPackIT extends AbstractAuctionIT {

  private static String csv() {
    var rows = new StringBuilder("name,country,role,tags,base_price,set,provider_id\n");
    String[] roles = {"gk", "def", "mid", "fwd"};
    String[] sets = {"Keepers", "Defence", "Midfield", "Attack"};
    for (int i = 0; i < 20; i++) {
      long price = i == 7 ? 5_250_000 : 300_000 + (i % 5) * 100_000L;
      rows.append("Fake ")
          .append(i + 1)
          .append(",Spain,")
          .append(roles[i % 4])
          .append(",")
          .append(i % 2 == 0 ? "local" : "foreign")
          .append(",")
          .append(price)
          .append(",")
          .append(sets[i / 5])
          .append(",\n");
    }
    return rows.toString();
  }

  @Test
  void aScriptedAuctionRunsToEndedWithSoldAndUnsoldLotsOnTheSecondPack() throws Exception {
    assertThat(importSeason("stub-football", csv()).imported()).isEqualTo(20);
    Person commissioner = person();
    String roomId = createRoom(commissioner, "stub-football", "standard");
    Person a = insertManager(roomId, "crimson");
    Person b = insertManager(roomId, "sky");
    UUID fa = franchise(roomId, a);
    UUID fb = franchise(roomId, b);
    ObjectNode config = (ObjectNode) body(get(commissioner, "/api/rooms/" + roomId)).get("config");
    config.put("squadMin", 3).put("squadMax", 20);
    config.putObject("roleMinimums");
    var saved =
        put(
            commissioner,
            "/api/rooms/" + roomId + "/config",
            Map.of("version", 1, "config", config));
    assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
    Room room = new Room(roomId, commissioner, List.of(a, b), List.of(fa, fb));

    goLive(room);
    JsonNode live = state(room, commissioner);
    assertThat(live.get("lotCount").asInt()).isEqualTo(20);
    assertThat(live.get("lot").get("name").asText()).isEqualTo("Fake 1");
    assertThat(live.get("config").get("ladder")).hasSize(2);

    long spent = 0;
    int sold = 0;
    int unsold = 0;
    for (int lot = 1; lot <= 20; lot++) {
      JsonNode now = state(room, commissioner);
      assertThat(now.get("lot").get("no").asInt()).isEqualTo(lot);
      long base = now.get("lot").get("basePrice").asLong();
      if (lot % 3 == 0) {
        unsold++;
        advance(20);
      } else if (base == 5_250_000) {
        // Above the 5 000 000 band edge the step is 250 000.
        assertThat(outcome(bid(room, a, lot, base))).isEqualTo("accepted");
        assertThat(outcome(bid(room, b, lot, base + 250_000))).isEqualTo("accepted");
        assertThat(outcome(bid(room, a, lot, base + 300_000))).isEqualTo("rejected:outbid");
        spent += base + 250_000;
        sold++;
        advance(10);
      } else {
        assertThat(outcome(bid(room, lot % 2 == 0 ? a : b, lot, base))).isEqualTo("accepted");
        spent += base;
        sold++;
        advance(10);
      }
      tick();
      if (lot < 20) {
        advance(5);
        tick();
      }
    }

    JsonNode end = state(room, commissioner);
    assertThat(end.get("phase").asText()).isEqualTo("ended");
    assertThat(end.get("unsold")).hasSize(unsold);
    assertThat(unsold).isGreaterThan(0);
    assertThat(sold).isGreaterThan(0);
    long purses = 0;
    int squadSize = 0;
    for (JsonNode s : end.get("standings")) {
      purses += s.get("purse").asLong();
      squadSize += s.get("squad").size();
    }
    assertThat(squadSize).isEqualTo(sold);
    assertThat(purses).isEqualTo(2 * config.get("purse").asLong() - spent);
    assertThat(eventCount(room, "lot_sold")).isEqualTo(sold);
    assertThat(eventCount(room, "lot_unsold")).isEqualTo(unsold);
    assertThat(eventCount(room, "auction_ended")).isEqualTo(1);
  }

  private UUID franchise(String roomId, Person who) {
    return jdbc.queryForObject(
        "SELECT id FROM franchises WHERE room_id = ? AND owner_user_id = ?",
        UUID.class,
        UUID.fromString(roomId),
        userId(who));
  }

  @Test
  void theAgnosticCheckPassesSoNoCoreCodeNamesASportRoleStatOrCurrency() throws Exception {
    File script = new File("../scripts/check-agnostic.sh");
    assertThat(script).exists();
    Process process =
        new ProcessBuilder("bash", script.getPath()).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes());
    assertThat(process.waitFor()).as(output).isZero();
    assertThat(output.trim()).isEqualTo("check-agnostic: ok");
  }
}
