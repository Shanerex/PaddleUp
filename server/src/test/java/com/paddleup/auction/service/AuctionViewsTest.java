package com.paddleup.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The state view's own arithmetic. */
class AuctionViewsTest {

  @Test
  void resumeLobbySecondsRoundsUpSoALobbyIsNeverZeroSeconds() {
    assertThat(AuctionViews.lobbySeconds(Duration.ofMillis(500))).isEqualTo(1);
    assertThat(AuctionViews.lobbySeconds(Duration.ofMillis(90_500))).isEqualTo(91);
    assertThat(AuctionViews.lobbySeconds(Duration.ofMillis(1))).isEqualTo(1);
  }

  @Test
  void aWholeNumberOfSecondsIsUnchanged() {
    assertThat(AuctionViews.lobbySeconds(Duration.ofMinutes(2))).isEqualTo(120);
    assertThat(AuctionViews.lobbySeconds(Duration.ofSeconds(1))).isEqualTo(1);
  }
}
