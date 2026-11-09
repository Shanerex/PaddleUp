package com.paddleup.auction.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.auction.service.Presence;
import com.paddleup.auction.service.StompAuth;
import com.paddleup.auction.service.StompUser;
import jakarta.annotation.PreDestroy;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * The auction socket: endpoint {@code /api/ws}, broadcasts on {@code /topic}, no inbound
 * application destinations. The origin list is the web app's address. A handshake with a session
 * cookie carries that user on; one without is let through and must present a ticket on CONNECT.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  /** Longest STOMP frame a client may send; it only ever sends CONNECT and SUBSCRIBE. */
  private static final int MAX_FRAME_BYTES = 16 * 1024;

  /**
   * Heartbeat each way. Loose on purpose: a backgrounded tab or a 2-3 s mobile stall must not end
   * the session, flip presence to "not here" and lock bidding. A clean network loss is caught by the
   * browser's offline event (AC-13), a silent drop by missed beats within about 2 intervals.
   * Keep equal to {@code HEARTBEAT_MS} in web/src/auction/live.ts.
   */
  private static final long HEARTBEAT_MS = 10_000;

  private final ThreadPoolTaskScheduler heartbeats = new ThreadPoolTaskScheduler();
  private final StompAuth auth;
  private final Presence presence;
  private final CurrentUser currentUser;
  private final ObjectMapper mapper;
  private final String webBaseUrl;

  WebSocketConfig(
      StompAuth auth,
      Presence presence,
      CurrentUser currentUser,
      ObjectMapper mapper,
      @Value("${paddleup.auth.web-base-url}") String webBaseUrl) {
    this.auth = auth;
    this.presence = presence;
    this.currentUser = currentUser;
    this.mapper = mapper;
    this.webBaseUrl = webBaseUrl;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/api/ws")
        .setAllowedOrigins(webBaseUrl)
        .setHandshakeHandler(
            new DefaultHandshakeHandler() {
              @Override
              protected Principal determineUser(
                  ServerHttpRequest request,
                  WebSocketHandler wsHandler,
                  Map<String, Object> attributes) {
                return currentUser.find().map(StompUser::new).orElse(null);
              }
            });
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    heartbeats.setPoolSize(1);
    heartbeats.setThreadNamePrefix("stomp-heartbeat-");
    heartbeats.setDaemon(true);
    heartbeats.initialize();
    registry
        .enableSimpleBroker("/topic")
        .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
        .setTaskScheduler(heartbeats);
    registry.setPreservePublishOrder(true);
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new StompAuthInterceptor(auth, presence));
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration.setMessageSizeLimit(MAX_FRAME_BYTES).setTimeToFirstMessage(10_000);
  }

  @Override
  public boolean configureMessageConverters(List<MessageConverter> converters) {
    var resolver = new DefaultContentTypeResolver();
    resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
    var converter = new MappingJackson2MessageConverter();
    converter.setObjectMapper(mapper);
    converter.setContentTypeResolver(resolver);
    converters.add(converter);
    return false;
  }

  @PreDestroy
  void stopHeartbeats() {
    heartbeats.shutdown();
  }

  /** A socket that closes without a DISCONNECT frame still leaves the room. */
  @EventListener
  void closed(SessionDisconnectEvent event) {
    presence.leaveAll(event.getSessionId());
  }
}
