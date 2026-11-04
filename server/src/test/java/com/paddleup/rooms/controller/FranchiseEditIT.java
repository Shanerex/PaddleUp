package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.rooms.AbstractRoomsIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-7: the owner edits their franchise before the auction; nobody else can. */
class FranchiseEditIT extends AbstractRoomsIT {

  private String roomId;
  private Person commissioner;
  private Person ann;
  private String annFranchise;
  private Person ben;

  private void setUpRoom() {
    commissioner = person();
    roomId = createRoom(commissioner);
    String code = issueInvite(commissioner, roomId);
    ann = person();
    annFranchise =
        body(post(
                ann,
                "/api/join",
                Map.of(
                    "code",
                    code,
                    "franchise",
                    Map.of("name", "Ann XI", "colour", "sky", "monogram", "AX"))))
            .get("franchise")
            .get("id")
            .asText();
    ben = joinNew(roomId, code, "teal");
  }

  private String url() {
    return "/api/rooms/" + roomId + "/franchises/" + annFranchise;
  }

  @Test
  void theOwnerRenamesInSetupAndMarkupIsStored() {
    setUpRoom();

    var response = patch(ann, url(), Map.of("name", "<b>Ann</b> Kings", "monogram", "AK"));

    assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(body(response).get("name").asText()).isEqualTo("Ann Kings");
    assertThat(body(response).get("monogram").asText()).isEqualTo("AK");
    assertThat(body(response).get("colour").asText()).isEqualTo("sky");
  }

  @Test
  void theOwnerCanChangeColourToAFreeOneAndKeepTheirOwnName() {
    setUpRoom();

    var colour = patch(ann, url(), Map.of("colour", "rose"));
    var sameName = patch(ann, url(), Map.of("name", "ANN xi"));

    assertThat(colour.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(body(colour).get("colour").asText()).isEqualTo("rose");
    assertThat(sameName.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void editingIsRefusedWhenTheAuctionIsLive() {
    setUpRoom();
    jdbc.update("UPDATE rooms SET state = 'auction_live' WHERE id = ?", UUID.fromString(roomId));

    var response = patch(ann, url(), Map.of("name", "Late name"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(response).get("error").asText()).isEqualTo("room_locked");
    assertThat(
            jdbc.queryForObject(
                "SELECT name FROM franchises WHERE id = ?",
                String.class,
                UUID.fromString(annFranchise)))
        .isEqualTo("Ann XI");
  }

  @Test
  void anotherMemberAndTheCommissionerGet403() {
    setUpRoom();

    assertThat(patch(ben, url(), Map.of("name", "Hijack")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(patch(commissioner, url(), Map.of("name", "Hijack")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void aNonMemberGets404() {
    setUpRoom();

    assertThat(patch(person(), url(), Map.of("name", "Hijack")).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void aFranchiseOfAnotherRoomIs404() {
    setUpRoom();
    Person other = person();
    String otherRoom = createRoom(other);

    var response =
        patch(
            other,
            "/api/rooms/" + otherRoom + "/franchises/" + annFranchise,
            Map.of("name", "Nope"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void badLengthsAndMonogramsAreRejected() {
    setUpRoom();

    assertThat(patch(ann, url(), Map.of("name", "A")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(patch(ann, url(), Map.of("name", "x".repeat(31))).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(patch(ann, url(), Map.of("monogram", "ABCD")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(patch(ann, url(), Map.of("colour", "mauve")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(patch(ann, url(), Map.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void aDuplicateNameIgnoringCaseOrATakenColourIs409() {
    setUpRoom();
    String benFranchise =
        jdbc.queryForObject(
            "SELECT id::text FROM franchises WHERE room_id = ? AND colour = 'teal'",
            String.class,
            UUID.fromString(roomId));
    String benName =
        jdbc.queryForObject(
            "SELECT name FROM franchises WHERE id = ?",
            String.class,
            UUID.fromString(benFranchise));

    var name = patch(ann, url(), Map.of("name", benName.toUpperCase()));
    var colour = patch(ann, url(), Map.of("colour", "teal"));

    assertThat(name.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(name).get("error").asText()).isEqualTo("duplicate");
    assertThat(colour.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }
}
