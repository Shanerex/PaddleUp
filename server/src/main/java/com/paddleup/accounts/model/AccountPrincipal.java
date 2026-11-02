package com.paddleup.accounts.model;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/** The session's principal. Holds the user id only, never tokens or profile data. */
public record AccountPrincipal(UUID userId) implements Principal, Serializable {

  @Override
  public String getName() {
    return userId.toString();
  }
}
