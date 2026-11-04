package com.paddleup.rooms.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.rooms.dto.RoomConfigDto;
import com.paddleup.rooms.dto.Violation;
import com.paddleup.rooms.exception.InvalidConfigException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * JSON going to and from the database. The app mapper caps what a request may carry; stored jsonb
 * is text Postgres re-spaced and the server itself wrote, so it is read with the same modules but
 * without the request's length limits. What is written is kept small instead: nulls are left out
 * and a config past {@link #CONFIG_MAX_CHARS} is refused before it is stored.
 */
public final class StoredJson {

  /** Most characters a saved config may serialise to. A real config is a few KB. */
  public static final int CONFIG_MAX_CHARS = 64 * 1024;

  /** Deeper than anything the server writes, shallow enough to stay off the stack. */
  private static final int READ_DEPTH_MAX = 200;

  private final ObjectMapper reader;
  private final ObjectMapper writer;

  /** Copies {@code app}; the app mapper itself is left as it is. */
  public StoredJson(ObjectMapper app) {
    this.reader = app.copy();
    this.reader
        .getFactory()
        .setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxDocumentLength(-1)
                .maxStringLength(Integer.MAX_VALUE)
                .maxNestingDepth(READ_DEPTH_MAX)
                .build());
    this.writer = app.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  public <T> T read(String text, Class<T> type) {
    try {
      return reader.readValue(text, type);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  public <T> T read(String text, com.fasterxml.jackson.core.type.TypeReference<T> type) {
    try {
      return reader.readValue(text, type);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  public String write(Object value) {
    try {
      return writer.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Serialises a config, or refuses with one {@code config} violation if it is too large. */
  public String writeConfig(RoomConfigDto config) {
    String text = write(config);
    if (text.length() > CONFIG_MAX_CHARS) {
      throw new InvalidConfigException(
          List.of(
              new Violation(
                  "config",
                  "Config is too large; it must stay under "
                      + CONFIG_MAX_CHARS / 1024
                      + " KB once saved")));
    }
    return text;
  }
}
