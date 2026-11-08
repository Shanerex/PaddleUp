package com.paddleup.auction.service;

import com.paddleup.auction.config.AuctionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On boot, rebuilds every auction that has not ended by replaying its log, then steps each once.
 * That one step covers a start that passed while the server was down, a lot whose deadline passed
 * (it resumes with a fresh T_bid) and a gap that simply continues.
 */
@Component
class AuctionRecovery {

  private static final Logger log = LoggerFactory.getLogger(AuctionRecovery.class);

  private final AuctionRegistry registry;
  private final AuctionProperties props;

  AuctionRecovery(AuctionRegistry registry, AuctionProperties props) {
    this.registry = registry;
    this.props = props;
  }

  @EventListener(ApplicationReadyEvent.class)
  void recover() {
    if (!props.recovery()) {
      return;
    }
    var runners = registry.recoverAll();
    log.info("Recovered {} unended auction(s) from their logs", runners.size());
    for (AuctionRunner runner : runners) {
      try {
        runner.tick().join();
      } catch (RuntimeException e) {
        log.warn("Recovery step failed; the ticker retries", e);
      }
    }
  }
}
