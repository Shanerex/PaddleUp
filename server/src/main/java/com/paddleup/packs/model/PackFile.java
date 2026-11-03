package com.paddleup.packs.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;

/** One pack file read from disk as a JSON tree. */
public record PackFile(Path path, JsonNode tree) {

  public String name() {
    return path.getFileName().toString();
  }
}
