package com.paddleup.packs.exception;

/** Thrown when a pack id is not in the registry. */
public class PackNotFoundException extends RuntimeException {

  private final String packId;

  public PackNotFoundException(String packId) {
    super("No pack with id '" + packId + "'");
    this.packId = packId;
  }

  public String packId() {
    return packId;
  }
}
