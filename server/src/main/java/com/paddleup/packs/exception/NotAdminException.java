package com.paddleup.packs.exception;

/** The caller is not on the app-admin allow-list; maps to 403. */
public class NotAdminException extends RuntimeException {

  public NotAdminException() {
    super("Only app admins can do this");
  }
}
