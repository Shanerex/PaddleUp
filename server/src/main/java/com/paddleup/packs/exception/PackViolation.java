package com.paddleup.packs.exception;

/**
 * One problem found in a pack file. {@code check} is a stable code such as {@code unknown-role}.
 */
public record PackViolation(String file, String packId, String check, String message) {

  @Override
  public String toString() {
    return file + " [pack " + packId + "] " + check + ": " + message;
  }
}
