package com.paddleup.rooms.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.rooms.RoomsTestSupport;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Security review S-4: a code shorter than 10 characters breaks the 50-bit floor. */
class RoomsPropertiesTest {

  private static RoomsProperties withLength(int length) {
    return new RoomsProperties(
        Duration.ofDays(7),
        length,
        2,
        10,
        "http://localhost:3000",
        new RoomsProperties.JoinThrottle(5, 20, Duration.ofMinutes(1)));
  }

  @Test
  void codeLengthBelowTenFailsAtStartup() {
    assertThatThrownBy(() -> withLength(9))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("codeLength");
  }

  @Test
  void tenAndTheDefaultAreAccepted() {
    assertThat(withLength(10).codeLength()).isEqualTo(10);
    assertThat(RoomsTestSupport.defaults().codeLength()).isEqualTo(12);
  }
}
