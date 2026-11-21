package com.paddleup.scoring.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.rooms.AbstractRoomsIT;
import com.paddleup.rooms.service.ScoringRoomPort;
import com.paddleup.scoring.service.MatchStatsService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;

/**
 * AC-1: a manual stat sheet on the stub football pack, end to end over HTTP, with zero core
 * changes: the fields are the pack's own stats, and a save recomputes with the pack's rules.
 */
@TestPropertySource(
    properties = {
      "paddleup.packs.dir=src/test/resources/packs/stub",
      "paddleup.admin.emails=stat-stub-admin@example.test"
    })
class ManualStatsStubPackIT extends AbstractRoomsIT {

  private static final String CSV =
      """
      name,country,role,tags,base_price,set,provider_id
      Ana Gil,Spain,gk,local,500000,Keepers,
      Bo Dan,Brazil,def,foreign,1000000,Defence,
      Di Fox,Chile,fwd,foreign,300000,Attack,
      """;

  @Autowired MatchStatsService matches;
  @Autowired ScoringRoomPort port;

  private void importStubSeason() {
    var parts = new LinkedMultiValueMap<String, Object>();
    parts.add("packId", "stub-football");
    parts.add(
        "file",
        new ByteArrayResource(CSV.getBytes(StandardCharsets.UTF_8)) {
          @Override
          public String getFilename() {
            return "football.csv";
          }
        });
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    headers.add(HttpHeaders.COOKIE, signIn("stat-stub-admin@example.test"));
    var imported =
        rest.exchange(
            "/api/admin/datasets", HttpMethod.POST, new HttpEntity<>(parts, headers), String.class);
    assertThat(imported.getStatusCode()).as(imported.getBody()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void theSheetIsThePacksStatSchemaAndASaveScoresWithThePacksRules() {
    importStubSeason();
    Person commissioner = person();
    UUID roomId = UUID.fromString(createRoom(commissioner, "stub-football", "standard"));
    UUID dataset = port.ruleInputs(roomId).datasetId();
    UUID player = port.players(roomId).get(0).poolPlayerId();
    Instant start = Instant.parse("2027-04-01T14:00:00Z");
    matches.upsertMatch(dataset, "s1", start, "Opener");
    clock.freeze(start.plusSeconds(60));
    String base = "/api/rooms/" + roomId + "/stat-entry/matches/s1/players/" + player;

    JsonNode sheet = body(get(commissioner, base));

    // goals has two event types; the other five stats are one field each.
    assertThat(sheet.get("fields")).hasSize(7);
    List<String> ids = sheet.get("fields").findValuesAsText("stat");
    assertThat(ids)
        .containsExactly(
            "goals",
            "goals",
            "assists",
            "saves",
            "passes-completed",
            "passes-attempted",
            "minutes");
    assertThat(sheet.get("fields").findValuesAsText("eventType"))
        .containsExactly("open-play", "penalty", "", "", "", "", "");
    assertThat(sheet.get("fields").findValuesAsText("input")).containsOnly("number");
    assertThat(sheet.get("fields").get(0).get("label").asText()).isEqualTo("Goals");
    assertThat(sheet.get("fields").get(0).get("max").decimalValue())
        .isEqualByComparingTo("1000000");
    assertThat(sheet.get("role").asText()).isEqualTo("gk");

    var saved =
        put(
            commissioner,
            base,
            Map.of(
                "fields",
                List.of(
                    Map.of("stat", "goals", "eventType", "penalty", "value", 2),
                    Map.of("stat", "assists", "value", 3))));

    assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
    JsonNode result = body(saved);
    assertThat(result.get("changedFields").asInt()).isEqualTo(2);
    assertThat(result.get("after").decimalValue())
        .isEqualByComparingTo(storedTotal(roomId, player));
    assertThat(result.get("after").decimalValue().signum()).isNotEqualTo(0);
    assertThat(
            body(get(commissioner, "/api/rooms/" + roomId + "/stat-entry/matches"))
                .get(0)
                .get("chip")
                .asText())
        .isEqualTo("MANUAL");

    var refused =
        put(commissioner, base, Map.of("fields", List.of(Map.of("stat", "goals", "value", 1))));
    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(body(refused).get("fields").get(0).get("reason").asText())
        .isEqualTo("unknown_event_type");
  }

  private java.math.BigDecimal storedTotal(UUID roomId, UUID player) {
    return jdbc.queryForObject(
        "SELECT total FROM scoring_points WHERE room_id = ? AND match_id = 's1'"
            + " AND pool_player_id = ?",
        java.math.BigDecimal.class,
        roomId,
        player);
  }
}
