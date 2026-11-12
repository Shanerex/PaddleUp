package com.paddleup.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AC-2: scoring main sources name no sport. A string literal equal to a pack, role, stat or event
 * id of any shipped or stub pack fails the build, so the engine can only learn those from a pack.
 */
class ScoringAgnosticTest {

  private static final Path SCORING = Path.of("src/main/java/com/paddleup/scoring");
  private static final List<Path> PACK_DIRS =
      List.of(Path.of("../packs"), Path.of("src/test/resources/packs"));

  private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
  private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

  /** Every pack, role, stat and event-type id found in the pack files under the given dirs. */
  static Set<String> packIds(List<Path> dirs) throws IOException {
    var mapper = new ObjectMapper();
    var ids = new TreeSet<String>();
    for (Path dir : dirs) {
      if (!Files.isDirectory(dir)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(dir)) {
        for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
          JsonNode pack = mapper.readTree(file.toFile());
          add(ids, pack.get("id"));
          for (JsonNode role : pack.path("roles")) {
            add(ids, role.get("id"));
          }
          for (JsonNode stat : pack.path("statSchema")) {
            add(ids, stat.get("id"));
            for (JsonNode type : stat.path("eventTypes")) {
              add(ids, type);
            }
          }
        }
      }
    }
    return ids;
  }

  private static void add(Set<String> ids, JsonNode node) {
    if (node != null && node.isTextual() && !node.asText().isBlank()) {
      ids.add(node.asText());
    }
  }

  /** One line per string literal in {@code root}'s Java files that equals one of the ids. */
  static List<String> violations(Path root, Set<String> ids) throws IOException {
    var found = new ArrayList<String>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        String code = COMMENTS.matcher(Files.readString(file)).replaceAll("");
        Matcher m = LITERAL.matcher(code);
        while (m.find()) {
          if (ids.contains(m.group(1))) {
            found.add(file.getFileName() + " has the literal " + m.group());
          }
        }
      }
    }
    return found;
  }

  @Test
  void theShippedAndStubPacksYieldIdsToCheck() throws IOException {
    assertThat(packIds(PACK_DIRS))
        .contains("cricket-t20", "stub-football", "BAT", "WK", "runs", "wickets", "lbw", "goals");
  }

  @Test
  void scoringMainSourcesHoldNoPackRoleStatOrEventLiteral() throws IOException {
    assertThat(violations(SCORING, packIds(PACK_DIRS))).isEmpty();
  }

  @Test
  void aPlantedLiteralIsCaughtAndCommentsAndOtherStringsAreNot(@TempDir Path dir)
      throws IOException {
    Files.writeString(
        dir.resolve("Bad.java"), "class Bad { String s = \"wickets\"; String ok = \"edit\"; }\n");
    Files.writeString(
        dir.resolve("Fine.java"),
        "class Fine { // the \"runs\" stat is read from the pack\n"
            + "  /* \"BAT\" */ String ok = \"stale_version\"; }\n");

    var found = violations(dir, packIds(PACK_DIRS));

    assertThat(found).hasSize(1);
    assertThat(found.get(0)).contains("Bad.java").contains("\"wickets\"");
  }
}
