package com.paddleup.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * A Java STOMP client for tests. It connects with a session cookie, a ticket or neither, collects
 * every broadcast it receives as JSON, and records any ERROR frame or transport failure.
 */
public final class StompProbe implements AutoCloseable {

  /** Broadcast payloads, in arrival order, across every subscription of this probe. */
  public final BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();

  /** ERROR frame messages and transport failures. */
  public final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

  /** When each seq arrived, in System.nanoTime units, for latency checks. */
  public final java.util.concurrent.ConcurrentMap<Long, Long> arrivedNanos =
      new java.util.concurrent.ConcurrentHashMap<>();

  private final CountDownLatch settled = new CountDownLatch(1);
  private volatile long[] heartbeat = {0, 0};
  private final WebSocketStompClient client;
  private volatile StompSession session;

  private StompProbe(ObjectMapper mapper) {
    this.client = new WebSocketStompClient(new StandardWebSocketClient());
    var converter = new MappingJackson2MessageConverter();
    converter.setObjectMapper(mapper);
    client.setMessageConverter(converter);
  }

  /**
   * Connects to {@code /api/ws}. Exactly one of {@code cookie} and {@code ticket} is normally set.
   * Returns once the session is CONNECTED, refused or failed; check {@link #connected()}.
   */
  public static StompProbe connect(
      ObjectMapper mapper, int port, String cookie, String ticket, String origin) throws Exception {
    return connect(mapper, port, cookie, ticket, origin, false);
  }

  /**
   * As {@link #connect(ObjectMapper, int, String, String, String)}. With {@code heartbeats} the
   * client asks for a 1 s heartbeat each way and treats a silent server as a transport error.
   */
  public static StompProbe connect(
      ObjectMapper mapper,
      int port,
      String cookie,
      String ticket,
      String origin,
      boolean heartbeats)
      throws Exception {
    StompProbe probe = new StompProbe(mapper);
    if (heartbeats) {
      var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
      scheduler.setPoolSize(1);
      scheduler.setDaemon(true);
      scheduler.initialize();
      probe.client.setTaskScheduler(scheduler);
      probe.client.setDefaultHeartbeat(new long[] {1000, 1000});
    }
    var http = new WebSocketHttpHeaders();
    if (cookie != null) {
      http.add("Cookie", cookie);
    }
    if (origin != null) {
      http.setOrigin(origin);
    }
    var stomp = new StompHeaders();
    if (ticket != null) {
      stomp.add("ticket", ticket);
    }
    probe.client.connectAsync(
        "ws://localhost:" + port + "/api/ws", http, stomp, probe.new Handler());
    probe.settled.await(5, TimeUnit.SECONDS);
    return probe;
  }

  /** The heartbeat the server agreed to in CONNECTED: send and receive intervals in ms. */
  public long[] heartbeat() {
    return heartbeat;
  }

  public boolean connected() {
    return session != null && session.isConnected();
  }

  /** Subscribes and returns at once; a refusal shows up in {@link #errors}. */
  public void subscribe(String destination) {
    session.subscribe(
        destination,
        new StompSessionHandlerAdapter() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return JsonNode.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            long at = System.nanoTime();
            JsonNode event = (JsonNode) payload;
            if (event.has("seq")) {
              arrivedNanos.putIfAbsent(event.get("seq").asLong(), at);
            }
            received.add(event);
          }
        });
  }

  /** Sends a frame, which the server must refuse. */
  public void send(String destination, Object payload) {
    session.send(destination, payload);
  }

  public JsonNode next(long millis) throws InterruptedException {
    return received.poll(millis, TimeUnit.MILLISECONDS);
  }

  public String nextError(long millis) throws InterruptedException {
    return errors.poll(millis, TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    try {
      if (session != null && session.isConnected()) {
        session.disconnect();
      }
    } catch (RuntimeException closedAlready) {
      // The server closes the socket after refusing a frame.
    }
    client.stop();
  }

  private final class Handler extends StompSessionHandlerAdapter {
    @Override
    public void afterConnected(StompSession connected, StompHeaders headers) {
      session = connected;
      heartbeat = headers.getHeartbeat() == null ? new long[] {0, 0} : headers.getHeartbeat();
      settled.countDown();
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      errors.add(String.valueOf(headers.getFirst("message")));
      settled.countDown();
    }

    @Override
    public void handleException(
        StompSession s, StompCommand command, StompHeaders headers, byte[] payload, Throwable ex) {
      errors.add("exception: " + ex);
      settled.countDown();
    }

    @Override
    public void handleTransportError(StompSession s, Throwable ex) {
      errors.add("transport: " + ex);
      settled.countDown();
    }
  }
}
