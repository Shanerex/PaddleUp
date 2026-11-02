package com.paddleup.accounts.service;

import com.paddleup.accounts.exception.EmailDeliveryException;

/** Port for outbound email. */
public interface EmailSender {

  /**
   * @throws EmailDeliveryException when the message could not be handed to the provider
   */
  void send(OutgoingEmail email);
}
