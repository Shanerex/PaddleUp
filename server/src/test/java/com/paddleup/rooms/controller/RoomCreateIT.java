package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.databind.JsonNode;
import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.service.PackService;
import com.paddleup.rooms.AbstractRoomsIT;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** AC-1: a room copies the pack preset and never reads the pack again. */
class RoomCreateIT extends AbstractRoomsIT {

  @MockitoSpyBean PackService packs;
  @Autowired PackService realPacks;

  @Test
  void createsASetupRoomWithTheCallerAsCommissionerAndThePresetAsConfig() {
    Person ann = person();
    PackDto pack = packs.get("cricket-t20");
    PresetDto preset = pack.presets().get(0);

    var response =
        post(
            ann,
            "/api/rooms",
            Map.of(
                "packId",
                "cricket-t20",
                "presetId",
                "standard",
                "name",
                "Friends <b>XI</b>",
                "competition",
                "Season 1"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode room = body(response);
    assertThat(room.get("state").asText()).isEqualTo("setup");
    assertThat(room.get("myRole").asText()).isEqualTo("commissioner");
    assertThat(room.get("commissioner").get("id").asText()).isEqualTo(userId(ann).toString());
    assertThat(room.get("configVersion").asInt()).isEqualTo(1);
    JsonNode config = room.get("config");
    assertThat(config.get("name").asText()).isEqualTo("Friends XI");
    assertThat(config.get("purse").asLong()).isEqualTo(preset.purse());
    assertThat(config.get("squadMin").asInt()).isEqualTo(preset.squadMin());
    assertThat(config.get("incrementLadder")).hasSize(preset.incrementLadder().size());
    assertThat(config.get("roleMinimums").get("WK").asInt()).isEqualTo(2);
    assertThat(config.get("categoryQuotas").get("overseas-cap").asInt()).isEqualTo(8);
    assertThat(config.get("ai").get("enabled").asBoolean()).isTrue();
    assertThat(config.get("ai").get("lifelines").get("fit-check").asInt()).isEqualTo(1);
    assertThat(config.get("ai").get("reportCardVisibility").asText()).isEqualTo("room");
    assertThat(config.get("scoringRules").get("rules")).isNotEmpty();
    assertThat(room.get("packLabels").get("packId").asText()).isEqualTo("cricket-t20");
    assertThat(room.get("franchiseCount").asInt()).isZero();
  }

  @Test
  void theRoomKeepsItsConfigAndPackVersionWhenThePackPresetsChange() {
    Person ann = person();
    String id = createRoom(ann);
    JsonNode before = body(get(ann, "/api/rooms/" + id));
    PackDto pack = realPacks.get("cricket-t20");
    var changed =
        new PresetDto(
            "standard",
            "Changed",
            1,
            "XXX",
            pack.presets().get(0).displayUnit(),
            pack.presets().get(0).incrementLadder(),
            1,
            2,
            Map.of(),
            pack.presets().get(0).retention());
    PackDto edited =
        new PackDto(
            pack.schemaVersion(),
            pack.id(),
            "9.9.9",
            pack.roles(),
            pack.tags(),
            pack.restrictedCategories(),
            List.of(changed),
            pack.statSchema(),
            pack.defaultRuleSet(),
            pack.appearanceRule(),
            pack.adapterKey(),
            pack.terminology(),
            pack.aiDomainContext());
    doReturn(edited).when(packs).get("cricket-t20");

    JsonNode after = body(get(ann, "/api/rooms/" + id));

    assertThat(after.get("config")).isEqualTo(before.get("config"));
    assertThat(after.get("packLabels").get("packVersion"))
        .isEqualTo(before.get("packLabels").get("packVersion"));
    assertThat(after.get("packLabels").get("packVersion").asText()).isNotEqualTo("9.9.9");
    assertThat(
            jdbc.queryForObject(
                "SELECT pack_version FROM rooms WHERE id = ?", String.class, UUID.fromString(id)))
        .isEqualTo(pack.version());
    Mockito.reset(packs);
  }

  @Test
  void anUnknownPresetOrPackIs400() {
    Person ann = person();

    var preset =
        post(
            ann, "/api/rooms", Map.of("packId", "cricket-t20", "presetId", "nope", "name", "Room"));
    var pack =
        post(ann, "/api/rooms", Map.of("packId", "nope", "presetId", "standard", "name", "Room"));

    assertThat(preset.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body(preset).get("field").asText()).isEqualTo("presetId");
    assertThat(pack.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body(pack).get("field").asText()).isEqualTo("packId");
  }

  @Test
  void aTooShortNameIs400NamingTheField() {
    var response =
        post(
            person(),
            "/api/rooms",
            Map.of("packId", "cricket-t20", "presetId", "standard", "name", "x"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body(response).get("violations").get(0).get("field").asText()).isEqualTo("name");
  }

  @Test
  void anUnreadableBodyIs400AndASignedOutCallerIs401() {
    var bad =
        call(org.springframework.http.HttpMethod.POST, "/api/rooms", person().cookie(), "[1]");
    var anonymous =
        post(
            null,
            "/api/rooms",
            Map.of("packId", "cricket-t20", "presetId", "standard", "name", "Room"));

    assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void listsThePacksWithTheirPresets() {
    var response = get(person(), "/api/rooms/packs");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode cricket = body(response).get(0);
    assertThat(cricket.get("id").asText()).isEqualTo("cricket-t20");
    assertThat(cricket.get("presets").get(0).get("id").asText()).isEqualTo("standard");
  }

  @Test
  void aCreatedRoomIsLogged() {
    Person ann = person();
    String id = createRoom(ann);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM room_log WHERE room_id = ? AND kind = 'room_created'",
                Integer.class,
                UUID.fromString(id)))
        .isEqualTo(1);
  }
}
