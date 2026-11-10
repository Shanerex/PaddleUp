package com.paddleup.packs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A role a player can hold in the pack's sport; {@code id} is opaque, {@code label} is shown.
 * {@code abbr} (JSON {@code short}) is an optional short key for tight spaces; null falls back to
 * the label.
 */
public record RoleDto(String id, String label, @JsonProperty("short") String abbr) {

  public RoleDto(String id, String label) {
    this(id, label, null);
  }
}
