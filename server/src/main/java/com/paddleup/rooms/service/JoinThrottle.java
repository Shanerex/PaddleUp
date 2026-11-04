package com.paddleup.rooms.service;

import com.paddleup.rooms.config.RoomsProperties;
import com.paddleup.rooms.exception.TooManyJoinAttemptsException;
import com.paddleup.rooms.repository.JoinThrottleRepository;
import com.paddleup.rooms.repository.JoinThrottleRepository.Usage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Sliding-window limits on failed invite-code attempts, per user and per client address, kept in
 * Postgres so every server instance shares them. Checked before the code is looked up, so a user or
 * address that has used up its failures is refused even with a valid code. Every method needs an
 * open transaction: the advisory locks last until commit, which makes check-then-record atomic.
 */
@Component
class JoinThrottle {

  static final String FAIL_USER = "join-fail-user";
  static final String FAIL_IP = "join-fail-ip";

  private final JoinThrottleRepository events;
  private final RoomsProperties.JoinThrottle limits;
  private final Clock clock;

  JoinThrottle(JoinThrottleRepository events, RoomsProperties props, Clock clock) {
    this.events = events;
    this.limits = props.joinThrottle();
    this.clock = clock;
  }

  /** Throws when the user or the address has used up its failed attempts in the window. */
  void admit(UUID userId, String clientKey) {
    Instant now = clock.instant();
    String user = hash(userId.toString());
    String ip = hash(clientKey);
    events.lock(FAIL_USER + ":" + user);
    events.lock(FAIL_IP + ":" + ip);
    events.purgeBefore(now.minus(limits.window()));
    require(FAIL_USER, user, limits.failedPerUser(), now);
    require(FAIL_IP, ip, limits.failedPerIp(), now);
  }

  /** Counts a failed attempt against the user and the address. */
  void recordFailure(UUID userId, String clientKey) {
    Instant now = clock.instant();
    events.record(FAIL_USER, hash(userId.toString()), now);
    events.record(FAIL_IP, hash(clientKey), now);
  }

  private void require(String scope, String keyHash, int limit, Instant now) {
    Duration window = limits.window();
    Usage usage = events.usage(scope, keyHash, now.minus(window));
    if (usage.count() >= limit) {
      Instant frees = usage.oldest().orElse(now).plus(window);
      throw new TooManyJoinAttemptsException(Duration.between(now, frees));
    }
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
