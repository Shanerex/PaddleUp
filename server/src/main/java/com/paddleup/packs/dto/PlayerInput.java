package com.paddleup.packs.dto;

import java.util.List;

/**
 * A player as typed or read from a file, before any rule is applied. The base price is raw text so
 * the rules decide what a bad number means.
 */
public record PlayerInput(
    String name,
    String country,
    String role,
    List<String> tags,
    String basePrice,
    String set,
    String providerId) {

  public PlayerInput {
    tags =
        tags == null
            ? List.of()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(tags));
  }
}
