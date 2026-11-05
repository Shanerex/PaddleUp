package com.paddleup.accounts.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code paddleup.admin}. {@code emails} is the app-admin allow-list (env {@code
 * PADDLEUP_ADMIN_EMAILS}, comma separated); the default, an empty list, admits nobody.
 */
@ConfigurationProperties("paddleup.admin")
public record AdminProperties(@DefaultValue List<String> emails) {

  public AdminProperties {
    emails = emails == null ? List.of() : List.copyOf(emails);
  }
}
