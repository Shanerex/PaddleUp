package com.paddleup.accounts.service;

import com.paddleup.accounts.config.AuthProperties;
import com.paddleup.accounts.exception.InvalidLoginException;
import com.paddleup.accounts.exception.InvalidRequestException;
import com.paddleup.accounts.exception.TooManyRequestsException;
import com.paddleup.accounts.model.LoginToken;
import com.paddleup.accounts.model.User;
import com.paddleup.accounts.repository.LoginTokenRepository;
import com.paddleup.accounts.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Issues and redeems single-use email sign-in tokens (link or 6-digit code). */
@Service
public class EmailSignInService {

  /** Raw secrets, returned once to the caller for delivery; never stored. */
  public record IssuedLogin(String email, String token, String code) {}

  private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
  private static final Pattern CODE = Pattern.compile("\\d{6}");
  static final String DEFAULT_AVATAR = "preset-01";
  private static final String FALLBACK_NAME = "Friend";

  private final SecureRandom random = new SecureRandom();
  private final LoginTokenRepository tokens;
  private final UserRepository users;
  private final AuthProperties props;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final EmailSender sender;
  private final AuthThrottle throttle;

  public EmailSignInService(
      LoginTokenRepository tokens,
      UserRepository users,
      AuthProperties props,
      Clock clock,
      TransactionTemplate tx,
      EmailSender sender,
      AuthThrottle throttle) {
    this.tokens = tokens;
    this.users = users;
    this.props = props;
    this.clock = clock;
    this.tx = tx;
    this.sender = sender;
    this.throttle = throttle;
  }

  private static boolean isValidEmail(String email) {
    return email.length() <= 254 && EMAIL.matcher(email).matches();
  }

  public static String normalise(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Issues a token and emails the recipient a link and a code. Throws {@link
   * TooManyRequestsException} before minting anything when the email or the client address is past
   * its limit.
   */
  public void start(String rawEmail, String clientIp) {
    String address = normalise(rawEmail);
    if (!isValidEmail(address)) {
      throw new InvalidRequestException("A valid email address is required");
    }
    IssuedLogin login =
        tx.execute(
            status -> {
              throttle.admitStart(address, clientIp);
              return issue(address);
            });
    String link = props.webBaseUrl() + "/auth/verify?token=" + login.token();
    sender.send(
        new OutgoingEmail(
            login.email(),
            "Your PaddleUp sign-in",
            "Sign in to PaddleUp with this link:\n"
                + link
                + "\n\nOr enter this code on the sign-in page.\nSign-in code: "
                + login.code()
                + "\n\nBoth expire in "
                + props.tokenTtl().toMinutes()
                + " minutes and work once. If you did not ask for this, ignore this email."));
  }

  /** Mints a token with no rate limit; production callers go through {@link #start}. */
  IssuedLogin issue(String rawEmail) {
    String email = normalise(rawEmail);
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String code = String.format("%06d", random.nextInt(1_000_000));
    UUID id = UUID.randomUUID();
    Instant now = clock.instant();
    tokens.insert(
        new LoginToken(
            id,
            email,
            sha256(token),
            codeHash(id, code),
            now.plus(props.tokenTtl()),
            null,
            0,
            now));
    return new IssuedLogin(email, token, code);
  }

  /** Spends the link token and returns its account, creating it on first sign-in. */
  public User redeemLink(String token, String clientIp) {
    Optional<String> email =
        tx.execute(
            status -> {
              throttle.beginVerify(null, clientIp);
              Optional<String> spent =
                  token == null || token.isBlank()
                      ? Optional.empty()
                      : tokens.consumeByLinkHash(sha256(token), clock.instant());
              if (spent.isEmpty()) {
                throttle.recordFailure(null, clientIp);
              }
              return spent;
            });
    return findOrCreate(email.orElseThrow(InvalidLoginException::new));
  }

  /**
   * Spends the newest open code for the email and returns its account. Wrong codes count against
   * the email across all its tokens and against the client address, so asking for new tokens does
   * not renew the guess budget.
   */
  public User redeemCode(String rawEmail, String code, String clientIp) {
    String email = normalise(rawEmail);
    boolean wellFormed = code != null && CODE.matcher(code).matches();
    Boolean spent = tx.execute(status -> spendCode(email, wellFormed ? code : null, clientIp));
    if (!Boolean.TRUE.equals(spent)) {
      throw new InvalidLoginException();
    }
    return findOrCreate(email);
  }

  private boolean spendCode(String email, String code, String clientIp) {
    if (throttle.beginVerify(email, clientIp)) {
      return false; // locked: do not even compare, and do not extend the lock
    }
    Instant now = clock.instant();
    Optional<LoginToken> open = tokens.findLatestOpenForUpdate(email, now);
    if (open.isEmpty()) {
      throttle.recordFailure(null, clientIp);
      return false;
    }
    LoginToken token = open.get();
    if (token.attempts() >= props.maxCodeAttempts()) {
      throttle.recordFailure(null, clientIp);
      return false;
    }
    boolean match =
        code != null
            && MessageDigest.isEqual(
                codeHash(token.id(), code).getBytes(StandardCharsets.UTF_8),
                token.codeHash().getBytes(StandardCharsets.UTF_8));
    if (!match) {
      tokens.incrementAttempts(token.id());
      throttle.recordFailure(email, clientIp);
      return false;
    }
    tokens.markUsed(token.id(), now);
    return true;
  }

  private User findOrCreate(String email) {
    return users
        .findByEmail(email)
        .orElseGet(
            () -> {
              Instant now = clock.instant();
              return users.insert(
                  new User(
                      UUID.randomUUID(),
                      email,
                      defaultName(null, email),
                      DEFAULT_AVATAR,
                      null,
                      now,
                      now));
            });
  }

  /** A first display name: the offered name if usable, else the email's local part. */
  static String defaultName(String offered, String email) {
    String name = DisplayNameSanitiser.sanitiseAndCut(offered);
    if (name.length() < 2) {
      String local = email.substring(0, Math.max(0, email.indexOf('@')));
      name = DisplayNameSanitiser.sanitiseAndCut(local.replaceAll("[^\\p{L}\\p{N}]+", " "));
    }
    return name.length() < 2 ? FALLBACK_NAME : name;
  }

  private static String codeHash(UUID tokenId, String code) {
    return sha256(tokenId + ":" + code);
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
