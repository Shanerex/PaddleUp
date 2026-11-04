package com.paddleup.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.rooms.ConfigEdit;
import com.paddleup.rooms.RoomsTestSupport;
import com.paddleup.rooms.dto.RoomConfigDto;
import com.paddleup.rooms.exception.InvalidConfigException;
import com.paddleup.rooms.util.StoredJson;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// Security review S-9: a saved config is capped by its own serialised size.
class StoredConfigSizeTest {

  private static RoomConfigDto configWithBands(int rules) {
    var pack = RoomsTestSupport.cricket();
    var factory = new RoomConfigFactory(RoomsTestSupport.defaults());
    var base = factory.create(pack, RoomsTestSupport.preset(pack, "standard"), "Friends", "S1");
    List<RuleDto> list = new ArrayList<>();
    for (int i = 0; i < rules; i++) {
      var bands = new ArrayList<RuleDto.Band>();
      for (int b = 0; b < 50; b++) {
        bands.add(
            new RuleDto.Band(
                BigDecimal.valueOf(-1_000_000), BigDecimal.valueOf(-1_000_000), BigDecimal.ONE));
      }
      list.add(new RuleDto.BandedRate("r" + i, "runs", "balls", BigDecimal.TEN, null, bands));
    }
    var e = ConfigEdit.of(base);
    e.scoringRules = new RuleSetDto(list);
    return e.build();
  }

  @Test
  void aRealConfigIsFarBelowTheCap() {
    var stored = new StoredJson(new ObjectMapper().findAndRegisterModules());
    var pack = RoomsTestSupport.cricket();
    var factory = new RoomConfigFactory(RoomsTestSupport.defaults());
    var real = factory.create(pack, RoomsTestSupport.preset(pack, "standard"), "Friends", "S1");

    assertThat(stored.writeConfig(real).length()).isLessThan(StoredJson.CONFIG_MAX_CHARS / 4);
  }

  @Test
  void aConfigPastTheSizeCapIsOneInvalidConfigViolation() {
    var stored = new StoredJson(new ObjectMapper().findAndRegisterModules());

    assertThatThrownBy(() -> stored.writeConfig(configWithBands(100)))
        .isInstanceOfSatisfying(
            InvalidConfigException.class,
            e -> {
              assertThat(e.violations()).hasSize(1);
              assertThat(e.violations().get(0).field()).isEqualTo("config");
              assertThat(e.violations().get(0).message()).contains("64 KB");
            });
  }
}
