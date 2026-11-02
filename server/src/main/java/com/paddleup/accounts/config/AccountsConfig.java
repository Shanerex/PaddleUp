package com.paddleup.accounts.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, ResendProperties.class})
class AccountsConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
