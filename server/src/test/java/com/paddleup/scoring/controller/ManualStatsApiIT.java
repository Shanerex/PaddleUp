package com.paddleup.scoring.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.auction.service.AuctionResultsPort;
import com.paddleup.scoring.ScoringIT;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * AC-4, AC-6, AC-8, AC-10, AC-11, AC-13, AC-14, AC-20: manual stat entry over real HTTP. The
 * auction results port is mocked so a test can say who owns a player.
 */
@TestPropertySource(properties = "paddleup.admin.emails=stat-admin@example.test")
class ManualStatsApiIT extends ScoringIT {

  @MockitoBean AuctionResultsPort auctionResults;

  private String base() {
    return "/api/rooms/" + roomId + "/stat-entry";
  }

  private String sheetUrl(UUID poolPlayer) {
    return base() + "/matches/m1/players/" + poolPlayer;
  }

  private static Map<String, Object> value(String stat, Object value) {
    var map = new java.util.LinkedHashMap<String, Object>();
    map.put("stat", stat);
    map.put("value", value);
    return map;
  }

  private ResponseEntity<String> save(Person who, UUID poolPlayer, Object... fields) {
    return put(who, sheetUrl(poolPlayer), Map.of("fields", List.of(fields)));
  }

  private void assertCode(ResponseEntity<String> response, HttpStatus status, String error) {
    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
    assertThat(body(response).get("error").asText()).isEqualTo(error);
  }

  @BeforeEach
  void started() {
    clock.freeze(START.plusSeconds(60));
    when(auctionResults.owners(roomId)).thenReturn(Map.of());
  }

  // ---- AC-10 ----

  @Test
  void theCommissionerReadsAndSavesAMemberGets403AndANonMemberOrAdminGets404() {
    UUID p = player.poolPlayerId();
    Person stranger = person();
    String email = "stat-admin@example.test";
    Person admin = new Person(email, signIn(email));

    assertThat(get(commissioner, base() + "/matches").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(get(commissioner, base() + "/matches/m1/players").getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(get(commissioner, sheetUrl(p)).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(save(commissioner, p, value("runs", 52)).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            post(
                    commissioner,
                    sheetUrl(p) + "/clear",
                    Map.of("fields", List.of(Map.of("stat", "runs"))))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    for (Person member : List.of(manager)) {
      assertCode(get(member, base() + "/matches"), HttpStatus.FORBIDDEN, "forbidden");
      assertCode(get(member, base() + "/matches/m1/players"), HttpStatus.FORBIDDEN, "forbidden");
      assertCode(get(member, sheetUrl(p)), HttpStatus.FORBIDDEN, "forbidden");
      assertCode(save(member, p, value("runs", 1)), HttpStatus.FORBIDDEN, "forbidden");
      assertCode(
          post(member, sheetUrl(p) + "/clear", Map.of("fields", List.of(Map.of("stat", "runs")))),
          HttpStatus.FORBIDDEN,
          "forbidden");
    }
    for (Person outsider : List.of(stranger, admin)) {
      assertCode(get(outsider, base() + "/matches"), HttpStatus.NOT_FOUND, "room_not_found");
      assertCode(save(outsider, p, value("runs", 1)), HttpStatus.NOT_FOUND, "room_not_found");
    }
    assertThat(get(null, base() + "/matches").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM scoring_stat_overrides WHERE room_id = ? AND stat = 'runs'"
                    + " AND value = 1",
                Integer.class,
                roomId))
        .isZero();
  }

  // ---- SEC-1, SEC-2 ----

  private ResponseEntity<String> raw(UUID poolPlayer, String json) {
    return call(
        org.springframework.http.HttpMethod.PUT, sheetUrl(poolPlayer), commissioner.cookie(), json);
  }

  @Test
  void anAbsurdExponentIs422NamingTheFieldAndReturnsFast() {
    UUID p = player.poolPlayerId();
    for (var entry : Map.of("1e999999999", "above_max", "-1e999999999", "below_min").entrySet()) {
      long started = System.nanoTime();
      var response = raw(p, "{\"fields\":[{\"stat\":\"runs\",\"value\":" + entry.getKey() + "}]}");

      assertCode(response, HttpStatus.UNPROCESSABLE_ENTITY, "invalid_stat_sheet");
      JsonNode fields = body(response).get("fields");
      assertThat(fields).hasSize(1);
      assertThat(fields.get(0).get("stat").asText()).isEqualTo("runs");
      assertThat(fields.get(0).get("reason").asText()).isEqualTo(entry.getValue());
      assertThat(fields.get(0).get("max").decimalValue()).isEqualByComparingTo("1000000");
      assertThat(System.nanoTime() - started).isLessThan(5_000_000_000L);
    }
  }

  @Test
  void moreFieldsThanTheSheetDeclaresIs400ForASaveAndAClear() {
    UUID p = player.poolPlayerId();
    var many = new java.util.ArrayList<Object>();
    for (int i = 0; i < 500; i++) {
      many.add(value("runs", 1));
    }
    var refs = new java.util.ArrayList<Object>();
    for (int i = 0; i < 500; i++) {
      refs.add(Map.of("stat", "runs"));
    }

    assertCode(
        put(commissioner, sheetUrl(p), Map.of("fields", many)),
        HttpStatus.BAD_REQUEST,
        "invalid_request");
    assertCode(
        post(commissioner, sheetUrl(p) + "/clear", Map.of("fields", refs)),
        HttpStatus.BAD_REQUEST,
        "invalid_request");
  }

  // ---- AC-6, AC-13, AC-8 ----

  @Test
  void aBadSheetIs422WithTheFieldAndItsRangeAndAnEmptyOneIs422EmptySheet() {
    var bad = save(commissioner, player.poolPlayerId(), value("fours", 3), value("runs", -1));

    assertCode(bad, HttpStatus.UNPROCESSABLE_ENTITY, "invalid_stat_sheet");
    JsonNode fields = body(bad).get("fields");
    assertThat(fields).hasSize(1);
    assertThat(fields.get(0).get("stat").asText()).isEqualTo("runs");
    assertThat(fields.get(0).get("reason").asText()).isEqualTo("below_min");
    assertThat(fields.get(0).get("min").decimalValue()).isEqualByComparingTo("0");
    assertThat(fields.get(0).get("max").decimalValue()).isEqualByComparingTo("1000000");
    assertThat(body(bad).get("message").asText())
        .isEqualTo("1 field needs fixing. Nothing was saved.");

    var empty = save(commissioner, player.poolPlayerId(), value("runs", null));
    assertCode(empty, HttpStatus.UNPROCESSABLE_ENTITY, "empty_sheet");
    assertThat(body(empty).get("message").asText()).contains("clear");
  }

  @Test
  void anUnknownMatchOrPlayerIs404AndAnUnstartedMatchIs409() {
    assertCode(
        put(
            commissioner,
            base() + "/matches/zz/players/" + player.poolPlayerId(),
            Map.of("fields", List.of(value("runs", 1)))),
        HttpStatus.NOT_FOUND,
        "unknown_match");
    assertCode(
        save(commissioner, UUID.randomUUID(), value("runs", 1)),
        HttpStatus.NOT_FOUND,
        "unknown_player");
    clock.freeze(START.minusSeconds(5));
    assertCode(
        save(commissioner, player.poolPlayerId(), value("runs", 1)),
        HttpStatus.CONFLICT,
        "match_not_started");
    assertCode(
        get(commissioner, base() + "/matches/m1/players"),
        HttpStatus.CONFLICT,
        "match_not_started");
    assertThat(body(get(commissioner, base() + "/matches")).get(0).get("chip").asText())
        .isEqualTo("UPCOMING");
  }

  // ---- AC-14, AC-20 ----

  @Test
  void theReturnedBeforeAndAfterEqualTheStoredRowsAndAnUnownedPlayerHasNoDelta() {
    provider(datasetId, "m1", player.sourcePlayerId(), "46");
    BigDecimal stored = storedTotal(player.poolPlayerId());

    var response = save(commissioner, player.poolPlayerId(), value("runs", "52.0"));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode result = body(response);
    assertThat(result.get("before").decimalValue()).isEqualByComparingTo(stored);
    assertThat(result.get("after").decimalValue())
        .isEqualByComparingTo(storedTotal(player.poolPlayerId()));
    assertThat(result.get("providerOnly").decimalValue()).isEqualByComparingTo(stored);
    assertThat(result.get("franchise").isNull()).isTrue();
    assertThat(result.get("changedFields").asInt()).isEqualTo(1);
    assertThat(result.get("sheet").get("owner").isNull()).isTrue();
    JsonNode runs = field(result.get("sheet"), "runs");
    assertThat(runs.get("manual").decimalValue()).isEqualByComparingTo("52");
    assertThat(runs.get("provider").decimalValue()).isEqualByComparingTo("46");
  }

  @Test
  void theFranchiseDeltaEqualsTheChangeInTheOwnersSumOfItsPlayersPoints() {
    var other = port.players(roomId).get(1);
    UUID franchise =
        jdbc.queryForObject(
            "SELECT id FROM franchises WHERE room_id = ? AND owner_user_id = ?",
            UUID.class,
            roomId,
            userId(manager));
    when(auctionResults.owners(roomId))
        .thenReturn(Map.of(player.poolPlayerId(), franchise, other.poolPlayerId(), franchise));
    assertThat(save(commissioner, other.poolPlayerId(), value("runs", 10)).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    BigDecimal sumBefore = franchiseSum();

    var response = save(commissioner, player.poolPlayerId(), value("runs", 52));

    JsonNode result = body(response);
    assertThat(result.get("franchise").get("id").asText()).isEqualTo(franchise.toString());
    assertThat(result.get("franchise").get("delta").decimalValue())
        .isEqualByComparingTo(franchiseSum().subtract(sumBefore));
    assertThat(result.get("franchise").get("delta").decimalValue())
        .isEqualByComparingTo(result.get("after").decimalValue());
    assertThat(result.get("sheet").get("owner").get("id").asText()).isEqualTo(franchise.toString());
    assertThat(result.get("sheet").get("owner").get("shortName").asText()).isNotBlank();

    var cleared =
        post(
            commissioner,
            sheetUrl(player.poolPlayerId()) + "/clear",
            Map.of("fields", List.of(Map.of("stat", "runs"))));
    assertThat(body(cleared).get("franchise").get("delta").decimalValue())
        .isEqualByComparingTo(result.get("after").decimalValue().negate());
  }

  // ---- AC-4, AC-11: the log ----

  @Test
  void anyMemberReadsTheOverrideLogPagedBySeqAndANonMemberGets404() {
    UUID p = player.poolPlayerId();
    save(commissioner, p, value("runs", 52), value("fours", 4));
    save(commissioner, p, value("runs", 60));
    post(commissioner, sheetUrl(p) + "/clear", Map.of("fields", List.of(Map.of("stat", "fours"))));
    String log = "/api/rooms/" + roomId + "/scoring/override-log";

    JsonNode all = body(get(manager, log));
    assertThat(all).hasSize(4);
    assertThat(all.get(0).get("oldValue").isNull()).isTrue();
    assertThat(all.get(2).get("oldValue").decimalValue()).isEqualByComparingTo("52");
    assertThat(all.get(2).get("newValue").decimalValue()).isEqualByComparingTo("60");
    assertThat(all.get(3).get("newValue").isNull()).isTrue();
    assertThat(all.get(3).get("stat").asText()).isEqualTo("fours");
    assertThat(all.get(0).get("actor").asText()).isEqualTo(userId(commissioner).toString());

    JsonNode first = body(get(commissioner, log + "?limit=2"));
    long last = first.get(1).get("seq").asLong();
    JsonNode rest = body(get(commissioner, log + "?limit=2&after=" + last));
    assertThat(first).hasSize(2);
    assertThat(rest).hasSize(2);
    assertThat(rest.get(0).get("seq").asLong()).isGreaterThan(last);

    assertCode(get(person(), log), HttpStatus.NOT_FOUND, "room_not_found");
  }

  private BigDecimal franchiseSum() {
    return jdbc.queryForObject(
        "SELECT coalesce(sum(total), 0) FROM scoring_points WHERE room_id = ? AND match_id = 'm1'",
        BigDecimal.class,
        roomId);
  }

  private BigDecimal storedTotal(UUID poolPlayer) {
    return jdbc.queryForObject(
        "SELECT total FROM scoring_points WHERE room_id = ? AND match_id = 'm1'"
            + " AND pool_player_id = ?",
        BigDecimal.class,
        roomId,
        poolPlayer);
  }

  private static JsonNode field(JsonNode sheet, String stat) {
    for (JsonNode f : sheet.get("fields")) {
      if (f.get("stat").asText().equals(stat)) {
        return f;
      }
    }
    throw new AssertionError("no field " + stat);
  }
}
