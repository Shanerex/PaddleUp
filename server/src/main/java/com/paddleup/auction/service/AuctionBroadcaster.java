package com.paddleup.auction.service;

import com.paddleup.auction.dto.AuctionEventDto;
import com.paddleup.auction.dto.PresenceDto;
import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

/**
 * Sends logged events to the room's topic. The runner calls it only after the events are committed,
 * so a client never sees an event that a restart could lose. A failed send is logged and dropped:
 * clients recover from the gap by refetching.
 */
@Component
public class AuctionBroadcaster {

  private static final Logger log = LoggerFactory.getLogger(AuctionBroadcaster.class);

  static String topic(UUID roomId) {
    return "/topic/auctions/" + roomId;
  }

  static String presenceTopic(UUID roomId) {
    return topic(roomId) + "/presence";
  }

  private final ObjectProvider<SimpMessageSendingOperations> messaging;
  private final Clock clock;

  AuctionBroadcaster(ObjectProvider<SimpMessageSendingOperations> messaging, Clock clock) {
    this.messaging = messaging;
    this.clock = clock;
  }

  /** Sends each committed event in seq order. {@code after} is the state they produced. */
  void events(UUID roomId, List<AuctionEvent> events, AuctionState after) {
    SimpMessageSendingOperations out = messaging.getIfAvailable();
    if (out == null) {
      return;
    }
    for (AuctionEvent e : events) {
      try {
        AuctionEventDto dto = AuctionViews.event(e, after, clock.instant());
        out.convertAndSend(topic(roomId), dto);
      } catch (RuntimeException ex) {
        log.warn("Broadcast of auction {} seq {} failed", roomId, e.seq(), ex);
      }
    }
  }

  void presence(UUID roomId, Set<UUID> connected, Set<UUID> ready) {
    SimpMessageSendingOperations out = messaging.getIfAvailable();
    if (out == null) {
      return;
    }
    try {
      out.convertAndSend(
          presenceTopic(roomId), new PresenceDto(List.copyOf(connected), List.copyOf(ready)));
    } catch (RuntimeException ex) {
      log.warn("Presence broadcast for auction {} failed", roomId, ex);
    }
  }
}
