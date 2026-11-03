package com.paddleup.packs.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.packs.dto.PackDisplayDto;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** The optional pack display section: name, summary and icon shown in the room wizard. */
class PackParserDisplayTest {

  private final ObjectMapper mapper = PackParser.newMapper();
  private final PackParser parser = new PackParser(mapper);

  private ObjectNode stub() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/packs/stub/stub-football.json")) {
      return (ObjectNode) mapper.readTree(in);
    }
  }

  private ObjectNode withDisplay(String json) throws Exception {
    var root = stub();
    root.set("display", mapper.readTree(json));
    return root;
  }

  @Test
  void packWithoutDisplayReadsEmpty() throws Exception {
    var root = stub();
    root.remove("display");
    var result = parser.parsePack(root, "p.json");
    assertThat(result.violations()).isEmpty();
    assertThat(result.value().display()).isEqualTo(PackDisplayDto.EMPTY);
  }

  @Test
  void displayFieldsAreRead() throws Exception {
    var result =
        parser.parsePack(
            withDisplay("{\"name\":\"Five-a-side\",\"summary\":\"Small pitch.\",\"icon\":\"sports\"}"),
            "p.json");
    assertThat(result.violations()).isEmpty();
    assertThat(result.value().display())
        .isEqualTo(new PackDisplayDto("Five-a-side", "Small pitch.", "sports"));
  }

  @Test
  void eachDisplayFieldIsOptional() throws Exception {
    var result = parser.parsePack(withDisplay("{\"name\":\"Five-a-side\"}"), "p.json");
    assertThat(result.violations()).isEmpty();
    assertThat(result.value().display()).isEqualTo(new PackDisplayDto("Five-a-side", null, null));
  }

  @Test
  void badDisplayValuesAreViolations() throws Exception {
    JsonNode bad =
        withDisplay(
            "{\"name\":\"  \",\"summary\":\"" + "x".repeat(281) + "\",\"icon\":\"<script>\"}");
    var result = parser.parsePack(bad, "p.json");
    assertThat(result.violations())
        .extracting(v -> v.message())
        .anySatisfy(m -> assertThat(m).contains("display.name"))
        .anySatisfy(m -> assertThat(m).contains("display.summary"))
        .anySatisfy(m -> assertThat(m).contains("display.icon"));
  }

  @Test
  void displayMustBeAnObject() throws Exception {
    var result = parser.parsePack(withDisplay("\"Football\""), "p.json");
    assertThat(result.violations())
        .extracting(v -> v.message())
        .anySatisfy(m -> assertThat(m).contains("section 'display' must be an object"));
  }

  @Test
  void bundledPacksHaveADisplayName() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/packs/stub/stub-football.json")) {
      var result = parser.parsePack(mapper.readTree(in), "stub-football.json");
      assertThat(result.value().display().name()).isEqualTo("Stub Football");
    }
  }
}
