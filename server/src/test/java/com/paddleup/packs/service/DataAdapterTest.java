package com.paddleup.packs.service;

import static com.paddleup.packs.PackTestSupport.STUB_DIR;
import static com.paddleup.packs.PackTestSupport.invalid;
import static com.paddleup.packs.PackTestSupport.loadFailure;
import static com.paddleup.packs.PackTestSupport.runner;
import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.StatLineDto;
import com.paddleup.packs.dto.StatSheetDto;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class DataAdapterTest {

  static final Path WITH_KEY =
      Path.of("src/test/resources/packs-adapter/with-key").toAbsolutePath();

  @Configuration
  static class DuplicateKeys {
    @Bean
    DuplicateKeyFeedAdapter secondFeed() {
      return new DuplicateKeyFeedAdapter();
    }
  }

  @Test
  void unknownAdapterKeyFailsStartup() {
    var failure = loadFailure(invalid("unknown-adapter-key"), StubFeedAdapter.class);

    assertThat(failure.violations()).extracting("check").containsExactly("unknown-adapter-key");
    assertThat(failure.getMessage()).contains("nonexistent", "pack.json", "stub-football");
  }

  @Test
  void duplicateAdapterKeysAcrossBeansFailStartup() {
    var failure = loadFailure(WITH_KEY, StubFeedAdapter.class, DuplicateKeys.class);

    assertThat(failure.violations()).extracting("check").contains("duplicate-adapter-key");
    assertThat(failure.getMessage()).contains("stub-feed");
  }

  @Test
  void packWithoutAdapterKeyLoadsAsManualEntryOnly() {
    runner(STUB_DIR, StubFeedAdapter.class)
        .run(
            ctx -> {
              var service = ctx.getBean(PackService.class);
              assertThat(service.list())
                  .singleElement()
                  .extracting("manualEntryOnly")
                  .isEqualTo(true);
              assertThat(service.get("stub-football").manualEntryOnly()).isTrue();
              assertThat(service.adapterFor("stub-football")).isEmpty();
            });
  }

  @Test
  void adapterStatSheetValidatesAgainstTheStubSchemaAndMapsAPlayer() {
    runner(WITH_KEY, StubFeedAdapter.class)
        .run(
            ctx -> {
              var service = ctx.getBean(PackService.class);
              assertThat(service.get("stub-football").manualEntryOnly()).isFalse();
              var adapter = service.adapterFor("stub-football").orElseThrow();
              assertThat(adapter).isInstanceOf(StubFeedAdapter.class);

              var sheet = adapter.fetchStats("m-1");
              assertThat(service.validateStatSheet("stub-football", sheet)).isEmpty();
              assertThat(adapter.mapPlayerId("ext-7")).contains("player-7");
              assertThat(adapter.listFixtures(Instant.EPOCH, Instant.EPOCH)).hasSize(1);
            });
  }

  @Test
  void statSheetWithUnknownStatOrEventTypeIsRejected() {
    runner(WITH_KEY, StubFeedAdapter.class)
        .run(
            ctx -> {
              var service = ctx.getBean(PackService.class);
              var sheet =
                  new StatSheetDto(
                      "m-1",
                      List.of(
                          new StatLineDto(
                              "p1",
                              Map.of("tackles", BigDecimal.ONE),
                              Map.of("goals", Map.of("header", 1)))));

              var problems = service.validateStatSheet("stub-football", sheet);

              assertThat(problems).hasSize(2);
              assertThat(problems.get(0)).contains("p1", "tackles");
              assertThat(problems.get(1)).contains("p1", "goals", "header");
            });
  }
}
