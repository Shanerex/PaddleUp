package com.paddleup.accounts.service;

import com.paddleup.accounts.config.AuthProperties;
import com.paddleup.accounts.exception.TooManyRequestsException;
import com.paddleup.accounts.repository.AuthThrottleRepository;
import com.paddleup.accounts.repository.AuthThrottleRepository.Usage;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Sliding-window limits for email sign-in, kept in Postgres so every server instance shares them.
 * Every method needs an open transaction: the advisory locks it takes last until commit, which
 * makes check-then-record atomic per key.
 */
@Service
class AuthThrottle {

  static final String START_EMAIL = "start-email";
  static final String START_IP = "start-ip";
  static final String CODE_FAIL_EMAIL = "code-fail-email";
  static final String FAIL_IP = "fail-ip";

  private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9a-fA-F:.]*:[0-9a-fA-F:.]*");

  private final AuthThrottleRepository events;
  private final AuthProperties.Throttle limits;
  private final Clock clock;

  AuthThrottle(AuthThrottleRepository events, AuthProperties props, Clock clock) {
    this.events = events;
    this.limits = props.throttle();
    this.clock = clock;
  }

  /** Admits one token issue for the email and address, or throws; counts it when admitted. */
  void admitStart(String email, String clientIp) {
    Instant now = clock.instant();
    String emailKey = hash(email);
    String ipKey = hash(ipKey(clientIp));
    events.lock(START_EMAIL + ":" + emailKey);
    events.lock(START_IP + ":" + ipKey);
    events.purgeBefore(now.minus(retention()));
    require(START_EMAIL, emailKey, limits.startPerEmail(), limits.startWindow(), now);
    require(START_IP, ipKey, limits.startPerIp(), limits.startWindow(), now);
    events.record(START_EMAIL, emailKey, now);
    events.record(START_IP, ipKey, now);
  }

  /**
   * Opens a verification attempt. Throws when the address has used up its failures. Returns true
   * when the email (null for a link) has used up its wrong-code budget, so the caller must not
   * compare the code.
   */
  boolean beginVerify(String email, String clientIp) {
    Instant now = clock.instant();
    String ipKey = hash(ipKey(clientIp));
    String emailKey = email == null ? null : hash(email);
    if (emailKey != null) {
      events.lock(CODE_FAIL_EMAIL + ":" + emailKey);
    }
    events.lock(FAIL_IP + ":" + ipKey);
    require(FAIL_IP, ipKey, limits.failedPerIp(), limits.failureWindow(), now);
    return emailKey != null
        && events.usage(CODE_FAIL_EMAIL, emailKey, now.minus(limits.failureWindow())).count()
            >= limits.failedCodesPerEmail();
  }

  /** Counts a failed attempt against the address, and against the email when it has one. */
  void recordFailure(String emailOrNull, String clientIp) {
    Instant now = clock.instant();
    events.purgeBefore(now.minus(retention()));
    events.record(FAIL_IP, hash(ipKey(clientIp)), now);
    if (emailOrNull != null) {
      events.record(CODE_FAIL_EMAIL, hash(emailOrNull), now);
    }
  }

  private void require(String scope, String keyHash, int limit, Duration window, Instant now) {
    Usage usage = events.usage(scope, keyHash, now.minus(window));
    if (usage.count() >= limit) {
      Instant frees = usage.oldest().orElse(now).plus(window);
      throw new TooManyRequestsException(Duration.between(now, frees));
    }
  }

  private Duration retention() {
    return limits.startWindow().compareTo(limits.failureWindow()) > 0
        ? limits.startWindow()
        : limits.failureWindow();
  }

  /** IPv6 addresses collapse to their /64 so one subscriber cannot rotate through its range. */
  static String ipKey(String clientIp) {
    String ip = clientIp == null ? "" : clientIp.trim();
    if (IPV6_LITERAL.matcher(ip).matches()) {
      try {
        byte[] raw = InetAddress.getByName(ip).getAddress();
        if (raw.length == 16) {
          return "v6:" + HexFormat.of().formatHex(Arrays.copyOf(raw, 8));
        }
      } catch (UnknownHostException e) {
        // not an address literal; fall through and use it as given
      }
    }
    return ip;
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
