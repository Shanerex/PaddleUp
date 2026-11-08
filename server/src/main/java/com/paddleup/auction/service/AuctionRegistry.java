package com.paddleup.auction.service;

import com.paddleup.auction.config.AuctionProperties;
import com.paddleup.auction.repository.AuctionEventRepository;
import com.paddleup.auction.repository.AuctionLotRepository;
import com.paddleup.auction.repository.AuctionRepository;
import com.paddleup.rooms.service.AuctionRoomPort;
import com.paddleup.rooms.service.RoomLifecycle;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Holds the one runner of each auction. A runner is built on first use by replaying its log, so a
 * room with nothing scheduled gets an empty one and a restarted server needs no special path.
 */
@Component
class AuctionRegistry {

  private final ConcurrentHashMap<UUID, AuctionRunner> runners = new ConcurrentHashMap<>();
  private final ExecutorService pool;
  private final AuctionRunner.Deps deps;
  private final AuctionRepository headers;

  AuctionRegistry(
      Clock clock,
      AuctionRepository headers,
      AuctionEventRepository events,
      AuctionLotRepository lots,
      PlatformTransactionManager transactions,
      AuctionBroadcaster broadcaster,
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
            new TransactionTemplate(transactions),
            broadcaster,
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

  /** Loads the runner of every auction that has not ended and returns them. */
  List<AuctionRunner> recoverAll() {
    return headers.unended().stream().map(this::runner).toList();
  }

  @PreDestroy
  void shutdown() {
    pool.shutdownNow();
  }
}
