package com.paddleup.auction.service;

import com.paddleup.auction.config.AuctionProperties;
import com.paddleup.auction.repository.AuctionEventRepository;
import com.paddleup.auction.repository.AuctionLotRepository;
import com.paddleup.auction.repository.AuctionRejectionRepository;
import com.paddleup.auction.repository.AuctionRepository;
import com.paddleup.rooms.service.AuctionRoomPort;
import com.paddleup.rooms.service.RoomLifecycle;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Holds the one runner of each auction. A runner is built on first use by replaying its log, so a
 * room with nothing scheduled gets an empty one and a restarted server needs no special path.
 */
@Component
class AuctionRegistry {

  private static final Logger log = LoggerFactory.getLogger(AuctionRegistry.class);

  private final ConcurrentHashMap<UUID, AuctionRunner> runners = new ConcurrentHashMap<>();
  private final ExecutorService pool;
  private final AuctionRunner.Deps deps;
  private final AuctionRepository headers;

  AuctionRegistry(
      Clock clock,
      AuctionRepository headers,
      AuctionEventRepository events,
      AuctionLotRepository lots,
      AuctionRejectionRepository rejections,
      PlatformTransactionManager transactions,
      AuctionBroadcaster broadcaster,
      Readiness readiness,
      List<BidGuard> guards,
      AuctionRoomPort port,
      RoomLifecycle lifecycle,
      AuctionProperties props) {
    var counter = new AtomicInteger();
    ThreadFactory threads =
        r -> {
          Thread t = new Thread(r, "auction-runner-" + counter.incrementAndGet());
          t.setDaemon(true);
          return t;
        };
    this.pool = Executors.newCachedThreadPool(threads);
    this.headers = headers;
    this.deps =
        new AuctionRunner.Deps(
            clock,
            headers,
            events,
            lots,
            rejections,
            new TransactionTemplate(transactions),
            broadcaster,
            readiness,
            List.copyOf(guards),
            port,
            lifecycle,
            props,
            pool);
  }

  /** The runner of {@code roomId}, rebuilt from the log on first use. */
  AuctionRunner runner(UUID roomId) {
    return runners.computeIfAbsent(roomId, id -> AuctionRunner.load(id, deps));
  }

  Collection<AuctionRunner> all() {
    return List.copyOf(runners.values());
  }

  /**
   * Loads the runner of every auction that has not ended and returns them. A room whose log cannot
   * be replayed is logged and left out, so one bad room never stops the server or the others; it is
   * tried again, and the error shows, when someone next asks for it.
   */
  List<AuctionRunner> recoverAll() {
    List<AuctionRunner> recovered = new ArrayList<>();
    for (UUID roomId : headers.unended()) {
      try {
        recovered.add(runner(roomId));
      } catch (RuntimeException e) {
        log.error("Auction {} was not recovered: its log cannot be replayed", roomId, e);
      }
    }
    return recovered;
  }

  @PreDestroy
  void shutdown() {
    pool.shutdownNow();
  }
}
