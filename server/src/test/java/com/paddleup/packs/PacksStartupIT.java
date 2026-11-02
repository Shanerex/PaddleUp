package com.paddleup.packs;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.PackSummaryDto;
import com.paddleup.packs.service.PackService;
import com.paddleup.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * AC-1: the full application registers the stub pack from the configured directory. This context
 * differs from the shared one, so its pool stays small to keep the shared Postgres under its
 * connection limit.
 */
@TestPropertySource(
    properties = {
      "paddleup.packs.dir=src/test/resources/packs/stub",
      "spring.datasource.hikari.maximum-pool-size=2"
    })
class PacksStartupIT extends AbstractPostgresIT {

  @Autowired PackService packs;

  @Test
  void listsTheStubPackIdAndVersion() {
    assertThat(packs.list()).contains(new PackSummaryDto("stub-football", "1.0.0", true));
  }
}
