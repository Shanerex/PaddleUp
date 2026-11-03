package com.paddleup.rooms.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.dto.RuleSetDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rules a room plays by, copied from a pack preset at creation and editable by the Commissioner
 * until the auction is live. Amounts are whole numbers of the currency's smallest unit; the display
 * unit says how to show them. Lists and maps are never null but may hold bad entries: the validator
 * reports those instead of the constructor throwing.
 */
public record RoomConfigDto(
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(requiredMode = REQUIRED) String competition,
    @Schema(requiredMode = REQUIRED) int minManagers,
    @Schema(requiredMode = REQUIRED) int maxManagers,
    @Schema(requiredMode = REQUIRED) long purse,
    @Schema(requiredMode = REQUIRED) String currencyCode,
    @Schema(requiredMode = REQUIRED) PresetDto.DisplayUnit displayUnit,
    @Schema(requiredMode = REQUIRED) List<PresetDto.IncrementStep> incrementLadder,
    @Schema(requiredMode = REQUIRED) int squadMin,
    @Schema(requiredMode = REQUIRED) int squadMax,
    @Schema(requiredMode = REQUIRED) Map<String, Integer> roleMinimums,
    @Schema(requiredMode = REQUIRED) Map<String, Integer> categoryQuotas,
    @Schema(requiredMode = REQUIRED) PresetDto.Retention retention,
    @Schema(requiredMode = REQUIRED) RuleSetDto scoringRules,
    @Schema(requiredMode = REQUIRED) AiSettingsDto ai) {

  public RoomConfigDto {
    incrementLadder = incrementLadder == null ? List.of() : new ArrayList<>(incrementLadder);
    roleMinimums = roleMinimums == null ? Map.of() : new LinkedHashMap<>(roleMinimums);
    categoryQuotas = categoryQuotas == null ? Map.of() : new LinkedHashMap<>(categoryQuotas);
    scoringRules = scoringRules == null ? new RuleSetDto(List.of()) : scoringRules;
  }

  public RoomConfigDto withName(String newName, String newCompetition) {
    return new RoomConfigDto(
        newName,
        newCompetition,
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
}
