package com.paddleup.packs.service;

import static com.paddleup.packs.PackTestSupport.STUB_DIR;
import static com.paddleup.packs.PackTestSupport.runner;
import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.RoleDto;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.packs.dto.TagDto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** NFR-12: a pack with other roles, stats, tags and currency loads through the same main code. */
class SecondPackTest {

  static final Path ALT_DIR = Path.of("src/test/resources/packs/alt").toAbsolutePath();

  @Test
  void theSecondPackLoadsThroughTheSameConfiguration() {
    runner(ALT_DIR)
        .run(
            ctx -> {
              var service = ctx.getBean(PackService.class);
              assertThat(service.list()).extracting("id").containsExactly("stub-volleyball");
              assertThat(service.get("stub-volleyball").roles()).hasSize(3);
            });
  }

  @Test
  void bothPacksRegisterSideBySideAndDifferInEveryPackDefinedSection(@TempDir Path tmp)
      throws Exception {
    Files.copy(STUB_DIR.resolve("stub-football.json"), tmp.resolve("stub-football.json"));
    Files.copy(ALT_DIR.resolve("stub-volleyball.json"), tmp.resolve("stub-volleyball.json"));

    runner(tmp)
        .run(
            ctx -> {
              var service = ctx.getBean(PackService.class);
              assertThat(service.list())
                  .extracting("id")
                  .containsExactly("stub-football", "stub-volleyball");

              PackDto a = service.get("stub-football");
              PackDto b = service.get("stub-volleyball");
              assertThat(ids(a.roles(), RoleDto::id))
                  .doesNotContainAnyElementsOf(ids(b.roles(), RoleDto::id));
              assertThat(ids(a.tags(), TagDto::id))
                  .doesNotContainAnyElementsOf(ids(b.tags(), TagDto::id));
              assertThat(ids(a.statSchema(), StatDto::id))
                  .doesNotContainAnyElementsOf(ids(b.statSchema(), StatDto::id));
              assertThat(a.presets().get(0).currencyCode())
                  .isNotEqualTo(b.presets().get(0).currencyCode());
              assertThat(a.presets().get(0).incrementLadder())
                  .isNotEqualTo(b.presets().get(0).incrementLadder());
              assertThat(a.presets().get(0).roleMinimums().keySet())
                  .doesNotContainAnyElementsOf(b.presets().get(0).roleMinimums().keySet());
            });
  }

  private static <T> List<String> ids(List<T> items, java.util.function.Function<T, String> id) {
    return items.stream().map(id).toList();
  }
}
