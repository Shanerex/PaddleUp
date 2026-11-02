package com.paddleup.accounts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Dev profile only: writes the message to the log instead of sending it. */
@Component
@Profile("dev")
class ConsoleEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

  @Override
  public void send(OutgoingEmail email) {
    log.info("[dev email] to={} subject={}\n{}", email.to(), email.subject(), email.text());
  }
}
