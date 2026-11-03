package com.paddleup.packs.dto;

/**
 * How a pack presents itself in the room wizard and join screen. Every field is optional; a
 * missing name falls back to the pack id in the UI. The icon is a Material Symbols name.
 */
public record PackDisplayDto(String name, String summary, String icon) {
  public static final PackDisplayDto EMPTY = new PackDisplayDto(null, null, null);
}
