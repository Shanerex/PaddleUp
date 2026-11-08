package com.paddleup.auction.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Event payloads going to and from jsonb. The server wrote them itself, so they are read without
 * the request limits: the {@code auction_started} snapshot grows with the pool.
 */
public final class PayloadJson {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final ObjectMapper mapper;

  /** Copies {@code app}; the app mapper itself is left as it is. */
  public PayloadJson(ObjectMapper app) {
    this.mapper = app.copy();
    this.mapper
        .getFactory()
        .setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxDocumentLength(-1)
                .maxStringLength(Integer.MAX_VALUE)
                .maxNestingDepth(200)
                .build());
  }

  public Map<String, Object> read(String text) {
    try {
      return mapper.readValue(text, MAP);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  public String write(Map<String, Object> payload) {
    try {
      return mapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
