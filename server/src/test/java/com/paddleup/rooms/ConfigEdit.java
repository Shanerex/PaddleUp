package com.paddleup.rooms;

import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.rooms.dto.AiSettingsDto;
import com.paddleup.rooms.dto.RoomConfigDto;
import java.util.List;
import java.util.Map;

/** A mutable copy of a config for tests that change one field. */
public final class ConfigEdit {

  public String name;
  public String competition;
  public int minManagers;
  public int maxManagers;
  public long purse;
  public String currencyCode;
  public PresetDto.DisplayUnit displayUnit;
  public List<PresetDto.IncrementStep> incrementLadder;
  public int squadMin;
  public int squadMax;
  public Map<String, Integer> roleMinimums;
  public Map<String, Integer> categoryQuotas;
  public PresetDto.Retention retention;
  public RuleSetDto scoringRules;
  public AiSettingsDto ai;

  public ConfigEdit(RoomConfigDto c) {
    name = c.name();
    competition = c.competition();
    minManagers = c.minManagers();
    maxManagers = c.maxManagers();
    purse = c.purse();
    currencyCode = c.currencyCode();
    displayUnit = c.displayUnit();
    incrementLadder = c.incrementLadder();
    squadMin = c.squadMin();
    squadMax = c.squadMax();
    roleMinimums = c.roleMinimums();
    categoryQuotas = c.categoryQuotas();
    retention = c.retention();
    scoringRules = c.scoringRules();
    ai = c.ai();
  }

  public RoomConfigDto build() {
    return new RoomConfigDto(
        name,
        competition,
        minManagers,
        maxManagers,
        purse,
        currencyCode,
        displayUnit,
        incrementLadder,
        squadMin,
        squadMax,
        roleMinimums,
        categoryQuotas,
        retention,
        scoringRules,
        ai);
  }

  public static ConfigEdit of(RoomConfigDto c) {
    return new ConfigEdit(c);
  }
}
