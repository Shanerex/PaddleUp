package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.rooms.AbstractRoomsIT;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** AC-4: the Commissioner issues, reads, revokes and regenerates one invite code. */
class InviteIT extends AbstractRoomsIT {

  private List<String> logKinds(String roomId) {
    return jdbc.queryForList(
        "SELECT kind FROM room_log WHERE room_id = ? AND kind LIKE 'invite_%' ORDER BY id",
        String.class, UUID.fromString(roomId));
  }

  @Test
  void issuesACodeOfTwelveCharactersAndALinkValidForSevenDays() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);

    var response = post(commissioner, "/api/rooms/" + roomId + "/invite", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var invite = body(response);
    String code = invite.get("code").asText();
    assertThat(code).matches("[0-9A-HJKMNP-TV-Z]{12}");
    assertThat(invite.get("link").asText()).endsWith("/join/" + code);
    Instant expires = Instant.parse(invite.get("expiresAt").asText());
    assertThat(Duration.between(Instant.now(), expires))
        .isBetween(Duration.ofDays(7).minusMinutes(1), Duration.ofDays(7));
    assertThat(logKinds(roomId)).containsExactly("invite_issued");
  }

  @Test
  void theActiveInviteCanBeReadBackAndIsMissingBeforeTheFirstIssue() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);

    var none = get(commissioner, "/api/rooms/" + roomId + "/invite");
    String code = issueInvite(commissioner, roomId);
    var current = get(commissioner, "/api/rooms/" + roomId + "/invite");

    assertThat(none.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(body(none).get("error").asText()).isEqualTo("no_invite");
    assertThat(body(current).get("code").asText()).isEqualTo(code);
  }

  @Test
  void regeneratingGivesANewCodeAndRevokesTheOldOne() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    String first = issueInvite(commissioner, roomId);

    String second = issueInvite(commissioner, roomId);

    assertThat(second).isNotEqualTo(first);
    assertThat(body(get(commissioner, "/api/rooms/" + roomId + "/invite")).get("code").asText())
        .isEqualTo(second);
    assertThat(
            jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM room_invites WHERE code = ?",
                Boolean.class,
                first))
        .isTrue();
    assertThat(logKinds(roomId)).containsExactly("invite_issued", "invite_regenerated");
  }

  @Test
  void revokingLeavesNoActiveInviteAndIsRepeatable() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    issueInvite(commissioner, roomId);

    var first = delete(commissioner, "/api/rooms/" + roomId + "/invite");
    var again = delete(commissioner, "/api/rooms/" + roomId + "/invite");

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(get(commissioner, "/api/rooms/" + roomId + "/invite").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(logKinds(roomId)).containsExactly("invite_issued", "invite_revoked");
  }

  @Test
  void aCodeCanBeIssuedAgainAfterARevoke() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    issueInvite(commissioner, roomId);
    delete(commissioner, "/api/rooms/" + roomId + "/invite");

    String code = issueInvite(commissioner, roomId);

    assertThat(code).hasSize(12);
  }

  @Test
  void noCodeCanBeIssuedOnceTheAuctionIsLive() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    jdbc.update("UPDATE rooms SET state = 'auction_live' WHERE id = ?", UUID.fromString(roomId));

    var response = post(commissioner, "/api/rooms/" + roomId + "/invite", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(body(response).get("error").asText()).isEqualTo("room_locked");
  }

  /** Security review S-3: the write needs a JSON content type, so a plain form post is refused. */
  @Test
  void issuingWithoutAJsonContentTypeIsRefused() {
    var commissioner = person();
    String roomId = createRoom(commissioner);
    var headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, commissioner.cookie());
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    var response =
        rest.exchange(
            "/api/rooms/" + roomId + "/invite",
            HttpMethod.POST,
            new HttpEntity<>("", headers),
            String.class);
    assertThat(response.getStatusCode().value()).isEqualTo(415);
  }
}
