package com.paddleup.auction.service;

import com.paddleup.accounts.dto.UserId;
import java.security.Principal;

/** The signed-in user of one socket session. */
public record StompUser(UserId user) implements Principal {

  @Override
  public String getName() {
    return user.value().toString();
  }
}
