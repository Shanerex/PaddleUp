package com.paddleup.scoring.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;

/**
 * JSON going to and from the database. The app mapper caps what a request may carry; stored jsonb
 * is text Postgres re-spaced and the server itself wrote, so it is read without those length
 * limits. What is written leaves nulls out, so an absent label stays absent.
 */
public final class StoredJson {

  /** Most characters a saved rule set may serialise to. */
  public static final int RULES_MAX_CHARS = 64 * 1024;

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
}
