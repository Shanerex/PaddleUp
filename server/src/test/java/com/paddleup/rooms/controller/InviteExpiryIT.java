package com.paddleup.rooms.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.rooms.AbstractRoomsIT;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** AC-5: invite expiry follows the server clock only. */
class InviteExpiryIT extends AbstractRoomsIT {

  private static final Instant ISSUED = Instant.parse("2031-03-01T10:00:00Z");

  @Test
  void aCodeRedeemsAtSixDaysTwentyThreeHoursAndIsRejectedAtSevenDays() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    Person early = person();
    Person late = person();
    clock.freeze(ISSUED);
    String code = issueInvite(commissioner, roomId);

    clock.freeze(ISSUED.plus(Duration.ofDays(6).plusHours(23)));
    var inTime = join(early, code, "sky");
    clock.freeze(ISSUED.plus(Duration.ofDays(7)));
    var expired = join(late, code, "teal");

    assertThat(inTime.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(body(expired).get("error").asText()).isEqualTo("invalid_invite");
  }

  @Test
  void anExpiredCodeIsRejectedWithTheSameErrorAsARevokedOne() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    clock.freeze(ISSUED);
    String expiring = issueInvite(commissioner, roomId);
    clock.freeze(ISSUED.plus(Duration.ofDays(8)));
    var expired = join(person(), expiring, "sky");
    String revoking = issueInvite(commissioner, roomId);
    delete(commissioner, "/api/rooms/" + roomId + "/invite");
    var revoked = join(person(), revoking, "sky");

    assertThat(expired.getStatusCode()).isEqualTo(revoked.getStatusCode());
    assertThat(expired.getBody()).isEqualTo(revoked.getBody());
  }

  @Test
  void regeneratingRestartsTheSevenDays() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    clock.freeze(ISSUED);
    issueInvite(commissioner, roomId);
    clock.freeze(ISSUED.plus(Duration.ofDays(5)));
    String fresh = issueInvite(commissioner, roomId);

    clock.freeze(ISSUED.plus(Duration.ofDays(5)).plus(Duration.ofDays(6)));
    var stillGood = join(person(), fresh, "sky");
    clock.freeze(ISSUED.plus(Duration.ofDays(5)).plus(Duration.ofDays(7)));
    var gone = join(person(), fresh, "teal");

    assertThat(stillGood.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void theExpiryShownIsSevenDaysAfterIssueByTheServerClock() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    clock.freeze(ISSUED);

    var invite = body(post(commissioner, "/api/rooms/" + roomId + "/invite", null));

    assertThat(Instant.parse(invite.get("expiresAt").asText()))
        .isEqualTo(ISSUED.plus(Duration.ofDays(7)));
  }

  @Test
  void anExpiredInviteIsNotReturnedByTheInviteEndpoint() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    clock.freeze(ISSUED);
    issueInvite(commissioner, roomId);
    clock.freeze(ISSUED.plus(Duration.ofDays(7)));

    assertThat(get(commissioner, "/api/rooms/" + roomId + "/invite").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void aClientSuppliedClockOrDateHeaderChangesNothing() {
    Person commissioner = person();
    String roomId = createRoom(commissioner);
    clock.freeze(ISSUED);
    String code = issueInvite(commissioner, roomId);
    clock.freeze(ISSUED.plus(Duration.ofDays(9)));
    var headers = new org.springframework.http.HttpHeaders();
    headers.add("Date", "Tue, 02 Mar 2031 10:00:00 GMT");
    headers.add("X-Client-Time", ISSUED.toString());
    headers.add("Cookie", person().cookie());
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

    var response =
        rest.exchange(
            "/api/join",
            org.springframework.http.HttpMethod.POST,
            new org.springframework.http.HttpEntity<>(
                java.util.Map.of("code", code, "franchise", franchiseBody("sky")), headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
