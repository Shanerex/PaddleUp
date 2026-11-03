package com.paddleup.packs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.packs.config.PacksConfig;
import com.paddleup.packs.dto.PackSummaryDto;
import com.paddleup.packs.exception.PackNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class PackServiceTest {

  static final Path STUB_DIR = Path.of("src/test/resources/packs/stub").toAbsolutePath();

  static ApplicationContextRunner runner(Path dir) {
    return new ApplicationContextRunner()
        .withUserConfiguration(PacksConfig.class, PackService.class)
        .withPropertyValues("paddleup.packs.dir=" + dir);
  }

  @Test
  void listsTheStubPackWithIdAndVersion() {
    runner(STUB_DIR)
        .run(
            ctx -> {
              var packs = ctx.getBean(PackService.class).list();
              assertThat(packs).containsExactly(new PackSummaryDto("stub-football", "1.0.0", true));
            });
  }

  @Test
  void getReturnsThePackAndUnknownIdThrows() {
    runner(STUB_DIR)
        .run(
            ctx -> {
              var service = ctx.getBean(PackService.class);
              assertThat(service.get("stub-football").roles()).hasSize(4);
              assertThatThrownBy(() -> service.get("nope"))
                  .isInstanceOf(PackNotFoundException.class)
                  .hasMessageContaining("nope");
            });
  }

  @Test
  void absentDirectoryStartsWithZeroPacksAndWarns(@TempDir Path tmp, CapturedOutput output) {
    var missing = tmp.resolve("missing");
    runner(missing)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx.getBean(PackService.class).list()).isEmpty();
            });
    assertThat(output.getAll()).contains("WARN").contains(missing.toString());
  }

  @Test
  void emptyDirectoryStartsWithZeroPacksAndWarns(@TempDir Path tmp, CapturedOutput output) {
    runner(tmp)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx.getBean(PackService.class).list()).isEmpty();
            });
    assertThat(output.getAll()).contains("WARN").contains(tmp.toString());
  }

  @Test
  void editingTheFileAndStartingAgainServesTheEditedValues(@TempDir Path tmp) throws Exception {
    var copy = tmp.resolve("stub-football.json");
    Files.copy(STUB_DIR.resolve("stub-football.json"), copy);
    runner(tmp)
        .run(
            ctx ->
                assertThat(ctx.getBean(PackService.class).get("stub-football").version())
                    .isEqualTo("1.0.0"));

    Files.writeString(copy, Files.readString(copy).replace("\"1.0.0\"", "\"2.5.0\""));

    runner(tmp)
        .run(
            ctx ->
                assertThat(ctx.getBean(PackService.class).get("stub-football").version())
                    .isEqualTo("2.5.0"));
  }
}
