package com.paddleup.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * App-wide limits on the JSON the server reads, for every module's request bodies. A body past a
 * limit fails to parse, which every module's handler answers with 400.
 */
@Configuration
class JsonLimitsConfig {

  /**
   * Deepest JSON nesting a body may have. Jackson's default (1000) is deep enough to overflow the
   * stack while a self-nesting rule is read. A real body is about a dozen levels deep.
   */
  static final int MAX_NESTING_DEPTH = 64;

  /** Longest whole JSON document, in characters. Bodies are small; a config is a few KB. */
  static final long MAX_DOCUMENT_LENGTH = 256 * 1024;

  /** Longest single JSON string value, in characters; free text is capped far below this. */
  static final int MAX_STRING_LENGTH = 16 * 1024;

  @Bean
  Jackson2ObjectMapperBuilderCustomizer jsonReadLimits() {
    return builder ->
        builder.postConfigurer(
            mapper ->
                mapper
                    .getFactory()
                    .setStreamReadConstraints(
                        StreamReadConstraints.builder()
                            .maxNestingDepth(MAX_NESTING_DEPTH)
                            .maxDocumentLength(MAX_DOCUMENT_LENGTH)
                            .maxStringLength(MAX_STRING_LENGTH)
                            .build()));
  }
}
