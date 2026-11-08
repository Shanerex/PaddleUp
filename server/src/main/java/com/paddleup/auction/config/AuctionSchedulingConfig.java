package com.paddleup.auction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** A scheduler for the auction clock only, so no other part of the app shares its thread. */
@Configuration
class AuctionSchedulingConfig {

  @Bean(name = "auctionScheduler")
  ThreadPoolTaskScheduler auctionScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("auction-clock-");
    scheduler.setDaemon(true);
    return scheduler;
  }
}
