package com.paddleup.packs.service;

import static com.paddleup.packs.PackTestSupport.STUB_DIR;
import static com.paddleup.packs.PackTestSupport.loadFailure;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.packs.exception.PackLoadException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** AC-1, AC-6: a stat may declare min and max; bad bounds are refused, old packs still load. */
class StatBoundsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @TempDir Path dir;

  private PackLoadException failWith(Consumer<ObjectNode> defect) throws Exception {
    var root = (ObjectNode) mapper.readTree(STUB_DIR.resolve("stub-football.json").toFile());
    defect.accept(root);
    Files.writeString(dir.resolve("pack.json"), mapper.writeValueAsString(root));
    return loadFailure(dir);
  }

  private static ObjectNode stat(ObjectNode root, int index) {
    return (ObjectNode) ((ArrayNode) root.get("statSchema")).get(index);
  }

  @ParameterizedTest(name = "min {0} max {1} is refused")
  @CsvSource({"-1, 5", "6, 5", "0, 1000001", "0.5, 5"})
  void badBoundsAreRefusedNamingTheStat(String min, String max) throws Exception {
    var failure =
        failWith(
            root -> {
              // stat 5 is a whole-number stat with no event types in the stub pack
              stat(root, 5).put("min", new java.math.BigDecimal(min));
              stat(root, 5).put("max", new java.math.BigDecimal(max));
            });

    assertThat(failure.violations()).extracting("check").containsExactly("stat-bounds");
    assertThat(failure.violations().get(0).message()).contains("minutes");
  }

  @Test
  void aFractionalBoundOnAWholeStatIsRefused() throws Exception {
    var failure = failWith(root -> stat(root, 5).put("max", new java.math.BigDecimal("2.5")));

    assertThat(failure.violations()).extracting("check").containsExactly("stat-bounds");
  }

  @Test
  void validBoundsLoadAndAreReadBack() throws Exception {
    var root = (ObjectNode) mapper.readTree(STUB_DIR.resolve("stub-football.json").toFile());
    stat(root, 5).put("min", 0).put("max", 1);
    Files.writeString(dir.resolve("pack.json"), mapper.writeValueAsString(root));

    com.paddleup.packs.PackTestSupport.runner(dir)
        .run(
            ctx -> {
              org.assertj.core.api.Assertions.assertThat(ctx).hasNotFailed();
              var loaded = ctx.getBean(PackService.class).get("stub-football");
              var minutes =
                  loaded.statSchema().stream()
                      .filter(s -> s.id().equals("minutes"))
                      .findFirst()
                      .orElseThrow();
              assertThat(minutes.max()).isEqualByComparingTo("1");
            });
  }

  @Test
  void aStatDtoWithoutBoundsHasNullBounds() {
    var stat = new StatDto("a", "A", "count", java.util.List.of());

    assertThat(stat.min()).isNull();
    assertThat(stat.max()).isNull();
  }
}
