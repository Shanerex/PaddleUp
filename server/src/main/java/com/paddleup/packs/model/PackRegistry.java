package com.paddleup.packs.model;

import java.util.Map;

/** The immutable set of loaded packs, keyed by pack id. Built once at startup. */
public record PackRegistry(Map<String, LoadedPack> packs) {
  public PackRegistry {
    packs = Map.copyOf(packs);
  }
}
