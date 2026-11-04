package com.paddleup.rooms.service;

import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.dto.RestrictedCategoryDto;
import com.paddleup.rooms.config.RoomsProperties;
import com.paddleup.rooms.dto.AiSettingsDto;
import com.paddleup.rooms.dto.PackLabelsDto;
import com.paddleup.rooms.dto.RoomConfigDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds a room's first config and pack snapshot from a pack preset. */
@Component
class RoomConfigFactory {

  /** Lifeline types, each with its own per-room count. */
  public static final List<String> LIFELINE_TYPES =
      List.of("fit-check", "price-ceiling", "rival-scan");

  public static final String REPORT_ROOM = "room";
  public static final String REPORT_PRIVATE = "private";
  public static final List<String> REPORT_VISIBILITIES = List.of(REPORT_ROOM, REPORT_PRIVATE);

  private static final int DEFAULT_LIFELINES = 1;

  private final RoomsProperties props;

  public RoomConfigFactory(RoomsProperties props) {
    this.props = props;
  }

  /** The preset's values plus the room defaults for managers and AI. */
  public RoomConfigDto create(PackDto pack, PresetDto preset, String name, String competition) {
    Map<String, Integer> quotas = new LinkedHashMap<>();
    for (RestrictedCategoryDto category : pack.restrictedCategories()) {
      quotas.put(category.id(), category.max());
    }
    Map<String, Integer> lifelines = new LinkedHashMap<>();
    LIFELINE_TYPES.forEach(type -> lifelines.put(type, DEFAULT_LIFELINES));
    return new RoomConfigDto(
        name,
        competition,
        props.defaultMinManagers(),
        props.defaultMaxManagers(),
        preset.purse(),
        preset.currencyCode(),
        preset.displayUnit(),
        preset.incrementLadder(),
        preset.squadMin(),
        preset.squadMax(),
        preset.roleMinimums(),
        quotas,
        preset.retention(),
        pack.defaultRuleSet(),
        new AiSettingsDto(true, lifelines, REPORT_ROOM));
  }

  /** The ids and labels the room keeps after creation. */
  public PackLabelsDto snapshot(PackDto pack) {
    return new PackLabelsDto(
        pack.id(),
        pack.version(),
        pack.roles(),
        pack.tags(),
        pack.restrictedCategories(),
        pack.statSchema(),
        pack.terminology(),
        pack.display().name());
  }
}
