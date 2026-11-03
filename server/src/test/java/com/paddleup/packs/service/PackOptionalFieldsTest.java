package com.paddleup.packs.service;

import static com.paddleup.packs.PackTestSupport.STUB_DIR;
import static com.paddleup.packs.PackTestSupport.runner;
import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.dto.StatDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The optional pack fields: a pack without them reads empty or null (AC-19). */
class PackOptionalFieldsTest {

  @Test
  void stubPackWithoutTheNewFieldsReadsEmptyOrNull() {
    runner(STUB_DIR)
        .run(
            ctx -> {
              var pack = ctx.getBean(PackService.class).get("stub-football");
              var preset = pack.presets().get(0);
              assertThat(preset.poolSize()).isNull();
              assertThat(preset.retention().maxRetentions()).isEqualTo(2);
              assertThat(preset.retention().rtmCards()).isEqualTo(1);
              assertThat(preset.retention().tagLimits()).isEmpty();
              assertThat(preset.retention().slabs()).isEmpty();
              assertThat(preset.retention().rtmFromSeason()).isNull();
              assertThat(preset.retention().rtmCounterRaise()).isNull();
              assertThat(pack.statSchema()).allSatisfy(s -> assertThat(s.unit()).isNull());
            });
  }

  @Test
  void oldArityConstructorsStillWork() {
    assertThat(new PresetDto.Retention(2, 1).slabs()).isEmpty();
    assertThat(new StatDto("a", "A", "count", List.of()).unit()).isNull();
    var retention = new PresetDto.Retention(6, 6, Map.of("x", 5), List.of(), 2, true);
    assertThat(retention.tagLimits()).containsEntry("x", 5);
    assertThat(new PresetDto.Slab("x", List.of(1L, 2L)).prices()).containsExactly(1L, 2L);
  }
}
