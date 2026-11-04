package com.paddleup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A module may use another module's service, dto and exception packages only; its controller,
 * repository, model, config and util packages are private. The check reads the main sources, so an
 * import or a fully qualified name both count.
 */
class ModuleBoundariesTest {

  private static final Path MAIN = Path.of("src/main/java/com/paddleup");
  private static final Set<String> PUBLIC_LAYERS = Set.of("service", "dto", "exception");

  /**
   * App-wide packages next to PaddleUpApplication; any module may use them, they use no private
   * layer.
   */
  private static final Set<String> APP_LEVEL = Set.of("config");

  private static final Pattern REFERENCE = Pattern.compile("com\\.paddleup\\.(\\w+)\\.(\\w+)");

  /** One line per reference that reaches into another module's private package. */
  static List<String> violations(Path root) throws IOException {
    var found = new ArrayList<String>();
    try (Stream<Path> modules = Files.list(root)) {
      for (Path module : modules.filter(Files::isDirectory).toList()) {
        String name = module.getFileName().toString();
        try (Stream<Path> files = Files.walk(module)) {
          for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
            Matcher m = REFERENCE.matcher(Files.readString(file));
            while (m.find()) {
              String other = m.group(1);
              String layer = m.group(2);
              if (!other.equals(name)
                  && !APP_LEVEL.contains(other)
                  && !PUBLIC_LAYERS.contains(layer)) {
                found.add(root.relativize(file) + " uses " + m.group());
              }
            }
          }
        }
      }
    }
    return found;
  }

  @Test
  void noModuleTouchesAnotherModulesPrivatePackages() throws IOException {
    assertThat(MAIN).isDirectory();
    assertThat(violations(MAIN)).isEmpty();
  }

  @Test
  void theScanSeesEveryModule() throws IOException {
    try (Stream<Path> modules = Files.list(MAIN)) {
      assertThat(modules.filter(Files::isDirectory).map(p -> p.getFileName().toString()))
          .contains("accounts", "rooms", "packs", "auction", "scoring", "advisor");
    }
  }

  @Test
  void theCheckCatchesARepositoryImportAndAFullyQualifiedModelUse(@TempDir Path dir)
      throws IOException {
    Path rooms = Files.createDirectories(dir.resolve("rooms/service"));
    Files.writeString(
        rooms.resolve("Bad.java"),
        """
        package com.paddleup.rooms.service;
        import com.paddleup.accounts.repository.UserRepository;
        import com.paddleup.accounts.service.CurrentUser;
        class Bad { com.paddleup.accounts.model.User user; com.paddleup.rooms.model.Room mine; }
        """);
    Files.createDirectories(dir.resolve("accounts"));

    assertThat(violations(dir))
        .containsExactlyInAnyOrder(
            "rooms/service/Bad.java uses com.paddleup.accounts.repository",
            "rooms/service/Bad.java uses com.paddleup.accounts.model");
  }

  @Test
  void anAppLevelConfigPackageIsSharedButMayNotReachIntoAModulesPrivatePackages(@TempDir Path dir)
      throws IOException {
    Path config = Files.createDirectories(dir.resolve("config"));
    Files.writeString(
        config.resolve("Shared.java"),
        """
        package com.paddleup.config;
        import com.paddleup.rooms.model.Room;
        class Shared {}
        """);
    Path rooms = Files.createDirectories(dir.resolve("rooms/service"));
    Files.writeString(
        rooms.resolve("Uses.java"),
        """
        package com.paddleup.rooms.service;
        import com.paddleup.config.JsonLimitsConfig;
        class Uses {}
        """);

    assertThat(violations(dir)).containsExactly("config/Shared.java uses com.paddleup.rooms.model");
  }

  @Test
  void theJsonLimitsLiveInTheAppLevelConfigNotInAModule() {
    assertThat(MAIN.resolve("config/JsonLimitsConfig.java")).isRegularFile();
    assertThat(MAIN.resolve("rooms/config/RoomsConfig.java"))
        .content()
        .doesNotContain("Jackson2ObjectMapperBuilderCustomizer");
  }
}
