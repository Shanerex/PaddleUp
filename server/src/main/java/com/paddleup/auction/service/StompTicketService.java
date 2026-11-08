package com.paddleup.auction.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.auction.config.AuctionProperties;
import com.paddleup.auction.dto.TicketDto;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Single-use tickets for opening the auction socket without a session cookie, as a browser on
 * another origin must. A ticket is bound to the user who asked, lives {@code ticketTtl} (30 s by
 * default), is kept in memory and works once.
 */
@Service
public class StompTicketService {

  private static final int BYTES = 32;
  private static final int MAX_LIVE = 10_000;

  private record Entry(UserId user, Instant expiresAt) {}

  private final ConcurrentHashMap<String, Entry> live = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();
  private final Clock clock;
  private final AuctionProperties props;

  StompTicketService(Clock clock, AuctionProperties props) {
    this.clock = clock;
    this.props = props;
  }

  public TicketDto issue(UserId user) {
    Instant now = clock.instant();
    live.values().removeIf(e -> !e.expiresAt().isAfter(now));
    if (live.size() >= MAX_LIVE) {
      throw new IllegalStateException("Too many live tickets");
    }
    byte[] raw = new byte[BYTES];
    random.nextBytes(raw);
    String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    Instant expiresAt = now.plus(props.ticketTtl());
    live.put(ticket, new Entry(user, expiresAt));
    return new TicketDto(ticket, expiresAt);
  }

  /** The user a ticket was issued to; the ticket is spent whether or not it was still valid. */
  public Optional<UserId> redeem(String ticket) {
    if (ticket == null) {
      return Optional.empty();
    }
    Entry entry = live.remove(ticket);
    if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
      return Optional.empty();
    }
    return Optional.of(entry.user());
  }
}
