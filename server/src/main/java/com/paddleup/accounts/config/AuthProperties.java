package com.paddleup.accounts.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under {@code paddleup.auth}. */
@ConfigurationProperties("paddleup.auth")
public record AuthProperties(
    @DefaultValue("15m") Duration tokenTtl,
    @DefaultValue("5") int maxCodeAttempts,
    @DefaultValue("http://localhost:3000") String webBaseUrl,
    @DefaultValue("12") int avatarPresetCount,
    @DefaultValue("true") boolean cookieSecure,
    @DefaultValue Throttle throttle) {

  /**
   * Sliding-window limits on email sign-in. Start limits cap tokens minted per email and per client
   * address; failure limits cap wrong guesses per email (across all its tokens) and per client
   * address. {@code trustedProxyHops} is how many proxies sit in front of the server (0 means trust
   * only the socket peer); the client address is then read from that many entries from the right of
   * {@code X-Forwarded-For}.
   */
  public record Throttle(
      @DefaultValue("3") int startPerEmail,
      @DefaultValue("10") int startPerIp,
      @DefaultValue("15m") Duration startWindow,
      @DefaultValue("5") int failedCodesPerEmail,
      @DefaultValue("20") int failedPerIp,
      @DefaultValue("1h") Duration failureWindow,
      @DefaultValue("0") int trustedProxyHops) {}
}
