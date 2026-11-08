package com.paddleup.auction.config;

import com.paddleup.auction.service.Presence;
import com.paddleup.auction.service.StompAuth;
import com.paddleup.auction.service.StompUser;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * Guards every inbound STOMP frame. CONNECT needs a session user or a valid ticket, SUBSCRIBE only
 * the caller's own room topics, and SEND is never allowed: bids and passes are REST intents, and
 * the socket carries broadcasts only.
 */
class StompAuthInterceptor implements ChannelInterceptor {

  private final StompAuth auth;
  private final Presence presence;

  StompAuthInterceptor(StompAuth auth, Presence presence) {
    this.auth = auth;
    this.presence = presence;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    StompCommand command = accessor == null ? null : accessor.getCommand();
    if (command == null) {
      return message; // a heartbeat
    }
    switch (command) {
      case CONNECT, STOMP -> {
        var user = auth.connect(accessor.getUser(), accessor.getFirstNativeHeader("ticket"));
        accessor.setUser(user.orElseThrow(() -> new MessageDeliveryException("Not signed in")));
      }
      case SUBSCRIBE -> {
        StompUser user = user(accessor);
        var seat = auth.subscribe(user.user(), accessor.getDestination());
        if (!seat.presenceTopic()) {
          presence.connect(
              accessor.getSessionId(),
              accessor.getSubscriptionId(),
              seat.roomId(),
              seat.franchiseId());
        }
      }
      case UNSUBSCRIBE -> presence.leave(accessor.getSessionId(), accessor.getSubscriptionId());
      case DISCONNECT -> presence.leaveAll(accessor.getSessionId());
      case SEND -> throw new MessageDeliveryException("Sending is not allowed");
      default -> {}
    }
    return message;
  }

  private static StompUser user(StompHeaderAccessor accessor) {
    if (accessor.getUser() instanceof StompUser user) {
      return user;
    }
    throw new MessageDeliveryException("Not signed in");
  }
}
