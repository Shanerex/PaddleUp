package com.paddleup.rooms.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

// Security review S-9 / QA T-3: stored jsonb is read without the request limits, and a saved
// config is capped by its own size.
class StoredJsonTest {

  private static ObjectMapper capped() {
    ObjectMapper mapper = new ObjectMapper();
    mapper
        .getFactory()
        .setStreamReadConstraints(
            StreamReadConstraints.builder().maxDocumentLength(1_000).maxNestingDepth(8).build());
    return mapper;
  }

  @Test
  void storedJsonPastTheRequestLimitsStillReads() {
    String big = "{\"a\":[" + "1,".repeat(5_000) + "1]}";
    assertThatThrownBy(() -> capped().readTree(big)).isInstanceOf(StreamConstraintsException.class);

    JsonNode read = new StoredJson(capped()).read(big, JsonNode.class);

    assertThat(read.get("a")).hasSize(5_001);
  }

  @Test
  void theRequestMapperKeepsItsLimits() {
    ObjectMapper request = capped();
    new StoredJson(request);
    assertThatThrownBy(() -> request.readTree("[" + "1,".repeat(5_000) + "1]"))
        .isInstanceOf(StreamConstraintsException.class);
  }

  @Test
  void nullsAreNotStored() {
    String written =
        new StoredJson(new ObjectMapper())
            .write(
                new java.util.HashMap<>() {
                  {
                    put("a", 1);
                    put("b", null);
                  }
                });
    assertThat(written).isEqualTo("{\"a\":1}");
  }
}
