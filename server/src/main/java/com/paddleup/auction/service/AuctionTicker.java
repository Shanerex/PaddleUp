package com.paddleup.auction.service;

import com.paddleup.auction.config.AuctionProperties;
import com.paddleup.auction.model.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * The auction clock. Every {@code paddleup.auction.tick} it steps each live or scheduled auction:
 * go live at the start instant, close a lot at its deadline, open the next after the gap. A tick of
 * zero turns it off; tests then call {@link #tickAll()} with a mutable clock.
 */
@Component
public class AuctionTicker implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(AuctionTicker.class);

  private final AuctionRegistry registry;
  private final AuctionProperties props;
  private final TaskScheduler scheduler;
  private ScheduledFuture<?> schedule;

  AuctionTicker(AuctionRegistry registry, AuctionProperties props, TaskScheduler auctionScheduler) {
    this.registry = registry;
    this.props = props;
    this.scheduler = auctionScheduler;
  }

  /** Steps every auction that can change with time, and returns when they have all finished. */
  public void tickAll() {
    List<CompletableFuture<Void>> pending = new ArrayList<>();
    for (AuctionRunner runner : registry.all()) {
      Phase phase = runner.state().phase();
      if (phase == Phase.SCHEDULED || phase == Phase.LIVE) {
        pending.add(runner.tick().exceptionally(AuctionTicker::failed));
      }
    }
    pending.forEach(CompletableFuture::join);
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
    schedule = scheduler.scheduleWithFixedDelay(this::tickAll, props.tick());
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
