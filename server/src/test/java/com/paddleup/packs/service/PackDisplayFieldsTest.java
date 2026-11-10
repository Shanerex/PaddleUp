package com.paddleup.packs.service;

import static com.paddleup.packs.PackTestSupport.STUB_DIR;
import static com.paddleup.packs.PackTestSupport.loadFailure;
import static com.paddleup.packs.PackTestSupport.runner;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.dto.RoleDto;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.packs.exception.PackLoadException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** AC-11: optional role short key, stat group and short and minor money units. */
class PackDisplayFieldsTest {

  private static final Path ALT_DIR = Path.of("src/test/resources/packs/alt").toAbsolutePath();

  private final ObjectMapper mapper = new ObjectMapper();

  @TempDir Path dir;

  private PackLoadException failWith(Consumer<ObjectNode> defect) throws Exception {
    var root = (ObjectNode) mapper.readTree(STUB_DIR.resolve("stub-football.json").toFile());
    defect.accept(root);
    Files.writeString(dir.resolve("pack.json"), mapper.writeValueAsString(root));
    return loadFailure(dir);
  }

  private static ObjectNode at(ObjectNode root, String pointer) {
    JsonNode node = root.at(pointer);
    assertThat(node.isObject()).as("fixture node %s", pointer).isTrue();
    return (ObjectNode) node;
  }

  @Test
  void theStubPackCarriesAllNewFields() {
    runner(STUB_DIR)
        .run(
            ctx -> {
              var pack = ctx.getBean(PackService.class).get("stub-football");
              assertThat(pack.roles())
                  .extracting(RoleDto::abbr)
                  .containsExactly("GK", "DEF", "MID", "FWD");
              assertThat(pack.statSchema()).extracting(StatDto::group).doesNotContainNull();
              var unit = pack.presets().get(0).displayUnit();
              assertThat(unit.abbr()).isEqualTo("M");
              assertThat(unit.minor()).isEqualTo(new PresetDto.DisplayUnit.Minor("K", "K", 1_000));
            });
  }

  @Test
  void aPackWithoutTheFieldsLoadsWithNulls() {
    runner(ALT_DIR)
        .run(
            ctx -> {
              var pack = ctx.getBean(PackService.class).get("stub-volleyball");
              assertThat(pack.roles()).allSatisfy(r -> assertThat(r.abbr()).isNull());
              assertThat(pack.statSchema()).allSatisfy(s -> assertThat(s.group()).isNull());
              var unit = pack.presets().get(0).displayUnit();
              assertThat(unit.abbr()).isNull();
              assertThat(unit.minor()).isNull();
            });
  }

  @Test
  void oldArityConstructorsStillWork() {
    assertThat(new RoleDto("a", "A").abbr()).isNull();
    assertThat(new StatDto("a", "A", "count", java.util.List.of(), "x").group()).isNull();
    assertThat(new PresetDto.DisplayUnit("M", 1_000_000L).minor()).isNull();
  }

  @ParameterizedTest(name = "{0}.{1} = {2} reports {3}")
  @CsvSource({
    "/roles/0, short, TOOLONG7, role-short",
    "/roles/0, short, '', role-short",
    "/roles/0, short, '<b>', role-short",
    "/statSchema/0, group, 'abcdefghijklmnopqrstuvwxy', stat-group",
    "/statSchema/0, group, ' ', stat-group",
    "/statSchema/0, group, '<i>x</i>', stat-group",
    "/presets/0/displayUnit, short, TOOLONG7, display-unit",
    "/presets/0/displayUnit, short, '<b>', display-unit"
  })
  void aBadTextValueIsAViolationAtLoad(String pointer, String field, String value, String check)
      throws Exception {
    var failure = failWith(root -> at(root, pointer).put(field, value));

    assertThat(failure.violations()).extracting("check").contains(check);
  }

  @ParameterizedTest(name = "minor.{0} = {1}")
  @CsvSource({
    "divisor, 0",
    "divisor, -5",
    "divisor, 1000000",
    "divisor, 2000000",
    "label, ''",
    "label, 'xxxxxxxxxxxxxxxxxxxxx'",
    "short, TOOLONG7",
    "short, ''"
  })
  void aBadMinorUnitIsAViolationAtLoad(String field, String value) throws Exception {
    var failure =
        failWith(
            root -> {
              var minor = at(root, "/presets/0/displayUnit/minor");
              if (field.equals("divisor")) {
                minor.put(field, Long.parseLong(value));
              } else {
                minor.put(field, value);
              }
            });

    assertThat(failure.violations()).extracting("check").contains("display-unit");
  }
}
