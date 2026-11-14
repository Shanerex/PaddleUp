package com.paddleup.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.PackDto;
import com.paddleup.rooms.ConfigEdit;
import com.paddleup.rooms.RoomsTestSupport;
import com.paddleup.rooms.dto.RoomConfigDto;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoomConfigFactoryTest {

  static final PackDto PACK = RoomsTestSupport.cricket();
  final RoomConfigFactory factory = new RoomConfigFactory(RoomsTestSupport.defaults());

  @ParameterizedTest
  @ValueSource(strings = {"standard", "short-pool"})
  void configEqualsThePresetPlusTheRoomDefaults(String presetId) {
    var preset = RoomsTestSupport.preset(PACK, presetId);

    RoomConfigDto config = factory.create(PACK, preset, "Friends", "Season 1");

    assertThat(config.name()).isEqualTo("Friends");
    assertThat(config.competition()).isEqualTo("Season 1");
    assertThat(config.purse()).isEqualTo(preset.purse());
    assertThat(config.currencyCode()).isEqualTo(preset.currencyCode());
    assertThat(config.displayUnit()).isEqualTo(preset.displayUnit());
    assertThat(config.incrementLadder()).isEqualTo(preset.incrementLadder());
    assertThat(config.squadMin()).isEqualTo(preset.squadMin());
    assertThat(config.squadMax()).isEqualTo(preset.squadMax());
    assertThat(config.roleMinimums()).isEqualTo(preset.roleMinimums());
    assertThat(config.retention()).isEqualTo(preset.retention());
    assertThat(config.scoringRules()).isEqualTo(PACK.defaultRuleSet());
    assertThat(config.categoryQuotas()).isEqualTo(Map.of("overseas-cap", 8));
    assertThat(config.minManagers()).isEqualTo(2);
    assertThat(config.maxManagers()).isEqualTo(10);
    assertThat(config.openSeconds()).isEqualTo(20);
    assertThat(config.bidSeconds()).isEqualTo(10);
    assertThat(config.gapSeconds()).isEqualTo(5);
  }

  @Test
  void aConfigWithoutTimersReadsTheDefaults() {
    var config =
        ConfigEdit.of(
            factory.create(PACK, RoomsTestSupport.preset(PACK, "standard"), "Friends", ""));
    config.openSeconds = null;
    config.bidSeconds = null;
    config.gapSeconds = null;

    var built = config.build();

    assertThat(built.openSeconds()).isEqualTo(20);
    assertThat(built.bidSeconds()).isEqualTo(10);
    assertThat(built.gapSeconds()).isEqualTo(5);
  }

  @Test
  void aiDefaultsAreOnWithOneLifelinePerTypeAndRoomWideReportCard() {
    var config = factory.create(PACK, RoomsTestSupport.preset(PACK, "standard"), "Friends", "");

    assertThat(config.ai().enabled()).isTrue();
    assertThat(config.ai().lifelines())
        .isEqualTo(Map.of("fit-check", 1, "price-ceiling", 1, "rival-scan", 1));
    assertThat(config.ai().reportCardVisibility()).isEqualTo("room");
  }

  @Test
  void snapshotCarriesThePackVocabulary() {
    var snapshot = factory.snapshot(PACK);

    assertThat(snapshot.packId()).isEqualTo("cricket-t20");
    assertThat(snapshot.packVersion()).isEqualTo(PACK.version());
    assertThat(snapshot.roles()).isEqualTo(PACK.roles());
    assertThat(snapshot.tags()).isEqualTo(PACK.tags());
    assertThat(snapshot.stats()).isEqualTo(PACK.statSchema());
    assertThat(snapshot.restrictedCategories()).isEqualTo(PACK.restrictedCategories());
  }

  @Test
  void snapshotCarriesThePacksAppearanceRule() {
    assertThat(factory.snapshot(PACK).appearanceRule())
        .isNotNull()
        .isEqualTo(PACK.appearanceRule());
  }
}
