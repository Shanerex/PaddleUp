package com.paddleup.rooms.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under {@code paddleup.rooms}. */
@ConfigurationProperties("paddleup.rooms")
public record RoomsProperties(
    @DefaultValue("7d") Duration inviteTtl,
    @DefaultValue("12") int codeLength,
    @DefaultValue("2") int defaultMinManagers,
    @DefaultValue("10") int defaultMaxManagers,
    @DefaultValue("http://localhost:3000") String webBaseUrl,
    @DefaultValue JoinThrottle joinThrottle) {

  /** Fewest code characters that keep the invite space at 50 bits or more. */
  public static final int MIN_CODE_LENGTH = 10;

  public RoomsProperties {
    if (codeLength < MIN_CODE_LENGTH) {
      throw new IllegalArgumentException(
          "paddleup.rooms.codeLength must be at least " + MIN_CODE_LENGTH);
    }
  }

  /**
   * Failed invite-code attempts allowed in {@code window}, per signed-in user and per client
   * address. Once either is used up, every redeem is refused, even for a valid code.
   */
  public record JoinThrottle(
      @DefaultValue("5") int failedPerUser,
      @DefaultValue("20") int failedPerIp,
      @DefaultValue("1m") Duration window) {}
}
