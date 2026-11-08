package com.paddleup.auction.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.rooms.dto.RoomAccessDto;
import com.paddleup.rooms.service.AuctionRoomPort;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The one auth check for the auction socket, whichever way a client came in. A same-origin browser
 * brings its session cookie on the handshake; any other client presents a single-use ticket on
 * CONNECT. Either way the result is a {@link StompUser}, and every SUBSCRIBE then applies the same
 * membership check the REST routes use.
 */
@Component
public class StompAuth {

  /** Only the room's own topics may be subscribed to; nothing else is offered. */
  private static final Pattern TOPIC =
      Pattern.compile("^/topic/auctions/([0-9a-fA-F-]{36})(/presence)?$");

  /** The room a subscription is for and the caller's franchise in it (null for a Commissioner). */
  public record Seat(UUID roomId, UUID franchiseId, boolean presenceTopic) {}

  private final StompTicketService tickets;
  private final AuctionRoomPort port;

  StompAuth(StompTicketService tickets, AuctionRoomPort port) {
    this.tickets = tickets;
    this.port = port;
  }

  /**
   * Who is connecting: the session user of the handshake if there is one, else the owner of a valid
   * ticket (which is spent). Empty when neither holds.
   */
  public Optional<StompUser> connect(Principal handshakeUser, String ticket) {
    if (handshakeUser instanceof StompUser user) {
      return Optional.of(user);
    }
    return tickets.redeem(ticket).map(StompUser::new);
  }

  /**
   * The seat a SUBSCRIBE asks for, if {@code destination} is one of this user's rooms' topics.
   * Throws a rooms {@code RoomNotFoundException} for a non-member, and an {@code
   * IllegalArgumentException} for any other destination.
   */
  public Seat subscribe(UserId user, String destination) {
    Matcher m = destination == null ? null : TOPIC.matcher(destination);
    if (m == null || !m.matches()) {
      throw new IllegalArgumentException("Not a subscribable destination");
    }
    UUID roomId;
    try {
      roomId = UUID.fromString(m.group(1));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Not a subscribable destination");
    }
    RoomAccessDto access = port.access(roomId, user);
    return new Seat(roomId, access.franchiseId(), m.group(2) != null);
  }
}
