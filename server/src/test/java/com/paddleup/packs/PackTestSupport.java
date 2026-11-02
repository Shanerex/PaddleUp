package com.paddleup.packs;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.config.PacksConfig;
import com.paddleup.packs.exception.PackLoadException;
import com.paddleup.packs.service.PackService;
import java.nio.file.Path;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Helpers for tests that start the packs module in a bare application context. */
public final class PackTestSupport {

  public static final Path STUB_DIR = Path.of("src/test/resources/packs/stub").toAbsolutePath();

  private PackTestSupport() {}

  public static ApplicationContextRunner runner(Path dir, Class<?>... extra) {
    var classes =
        new java.util.ArrayList<Class<?>>(java.util.List.of(PacksConfig.class, PackService.class));
    classes.addAll(java.util.List.of(extra));
    return new ApplicationContextRunner()
        .withUserConfiguration(classes.toArray(Class<?>[]::new))
        .withPropertyValues("paddleup.packs.dir=" + dir);
  }

  /** Path of a directory under {@code src/test/resources/packs-invalid}. */
  public static Path invalid(String name) {
    return Path.of("src/test/resources/packs-invalid", name).toAbsolutePath();
  }

  /**
   * Starts the context on the directory and returns the {@link PackLoadException} that aborted it.
   */
  public static PackLoadException loadFailure(Path dir, Class<?>... extra) {
    var holder = new PackLoadException[1];
    runner(dir, extra)
        .run(
            ctx -> {
              assertThat(ctx).hasFailed();
              Throwable t = ctx.getStartupFailure();
              while (t != null && !(t instanceof PackLoadException)) {
                t = t.getCause();
              }
              assertThat(t).as("startup failure cause").isInstanceOf(PackLoadException.class);
              holder[0] = (PackLoadException) t;
            });
    return holder[0];
  }

  /** Starts the context on a named invalid fixture and returns the load failure. */
  public static PackLoadException loadFailure(String fixture) {
    return loadFailure(invalid(fixture));
  }
}
