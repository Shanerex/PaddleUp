package com.paddleup.auction.service;

import com.paddleup.auction.config.AuctionProperties;
import com.paddleup.auction.model.Phase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * The auction clock. Every {@code paddleup.auction.tick} it steps each live or scheduled auction:
 * go live at the start instant, close a lot at its deadline, open the next after the gap. A tick of
 * zero turns it off; tests then call {@link #tickAll()} with a mutable clock. The scheduled tick
 * never waits on an auction, so a slow one cannot hold back the others.
 */
@Component
public class AuctionTicker implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(AuctionTicker.class);

  private static final Duration TICK_ALL_WAIT = Duration.ofSeconds(10);

  private final AuctionRegistry registry;
  private final AuctionProperties props;
  private final TaskScheduler scheduler;
  private ScheduledFuture<?> schedule;

  AuctionTicker(AuctionRegistry registry, AuctionProperties props, TaskScheduler auctionScheduler) {
    this.registry = registry;
    this.props = props;
    this.scheduler = auctionScheduler;
  }

  /**
   * Asks every auction that can change with time to step, and returns at once. The scheduler uses
   * this, so one slow or stuck auction never holds back the clocks of the others.
   */
  public List<CompletableFuture<Void>> requestTicks() {
    List<CompletableFuture<Void>> pending = new ArrayList<>();
    for (AuctionRunner runner : registry.all()) {
      Phase phase = runner.state().phase();
      if (phase == Phase.SCHEDULED || phase == Phase.LIVE) {
        pending.add(runner.tick().exceptionally(AuctionTicker::failed));
      }
    }
    return pending;
  }

  /**
   * Steps every auction and waits for them, for tests that move the clock by hand. It gives up
   * waiting after {@link #TICK_ALL_WAIT}; the ticks it started carry on.
   */
  public void tickAll() {
    var all = CompletableFuture.allOf(requestTicks().toArray(CompletableFuture[]::new));
    try {
      all.get(TICK_ALL_WAIT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      log.warn("Not every auction finished its tick within {}", TICK_ALL_WAIT);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      failed(e.getCause());
    }
  }

  private static Void failed(Throwable t) {
    log.warn("Auction tick failed; it is retried on the next tick", t);
    return null;
  }

  @Override
  public void start() {
    if (props.tick().isZero() || schedule != null) {
      return;
    }
    schedule = scheduler.scheduleWithFixedDelay(this::requestTicks, props.tick());
  }

  @Override
  public void stop() {
    if (schedule != null) {
      schedule.cancel(false);
      schedule = null;
    }
  }

  @Override
  public boolean isRunning() {
    return schedule != null;
  }
}
