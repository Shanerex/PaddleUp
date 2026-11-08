package com.paddleup.auction.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code paddleup.auction}.
 *
 * @param tick how often the clock step fires; zero turns the scheduler off, and tests then step a
 *     mutable clock by hand
 * @param ticketTtl how long a STOMP connect ticket lives
 * @param rejectionCacheSize rejected intents remembered per auction, so a resend gets its first
 *     answer
 * @param recovery whether boot rebuilds every unended auction from its log
 * @param intentTimeout how long a request waits for the auction to answer
 * @param eventsPageMax most events one log read returns
 */
@ConfigurationProperties("paddleup.auction")
public record AuctionProperties(
    @DefaultValue("100ms") Duration tick,
    @DefaultValue("30s") Duration ticketTtl,
    @DefaultValue("1000") int rejectionCacheSize,
    @DefaultValue("true") boolean recovery,
    @DefaultValue("10s") Duration intentTimeout,
    @DefaultValue("500") int eventsPageMax) {

  public AuctionProperties {
    if (rejectionCacheSize < 1 || eventsPageMax < 1) {
      throw new IllegalArgumentException("paddleup.auction sizes must be at least 1");
    }
  }
}
