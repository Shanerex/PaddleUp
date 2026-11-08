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
 * until the auction is live. The three timers are never null: a value that is absent (an older
 * stored config) reads as its default. Amounts are whole numbers of the currency's smallest unit;
 * the display unit says how to show them. Lists and maps are never null but may hold bad entries:
 * the validator reports those instead of the constructor throwing.
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
    @Schema(requiredMode = REQUIRED) AiSettingsDto ai,
    @Schema(
            description =
                "Seconds a lot waits for its first bid; absent reads as "
                    + DEFAULT_OPEN_SECONDS
                    + ", allowed 5 to 120")
        Integer openSeconds,
    @Schema(
            description =
                "Seconds a bid keeps the lot open; absent reads as "
                    + DEFAULT_BID_SECONDS
                    + ", allowed 3 to 60")
        Integer bidSeconds,
    @Schema(
            description =
                "Seconds between one lot closing and the next opening; absent reads as "
                    + DEFAULT_GAP_SECONDS
                    + ", allowed 0 to 30")
        Integer gapSeconds) {

  /** Auction timer defaults, used when a stored config has no value. */
  public static final int DEFAULT_OPEN_SECONDS = 20;

  public static final int DEFAULT_BID_SECONDS = 10;
  public static final int DEFAULT_GAP_SECONDS = 5;

  public RoomConfigDto {
    incrementLadder = incrementLadder == null ? List.of() : new ArrayList<>(incrementLadder);
    roleMinimums = roleMinimums == null ? Map.of() : new LinkedHashMap<>(roleMinimums);
    categoryQuotas = categoryQuotas == null ? Map.of() : new LinkedHashMap<>(categoryQuotas);
    scoringRules = scoringRules == null ? new RuleSetDto(List.of()) : scoringRules;
    openSeconds = openSeconds == null ? DEFAULT_OPEN_SECONDS : openSeconds;
    bidSeconds = bidSeconds == null ? DEFAULT_BID_SECONDS : bidSeconds;
    gapSeconds = gapSeconds == null ? DEFAULT_GAP_SECONDS : gapSeconds;
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
        ai,
        openSeconds,
        bidSeconds,
        gapSeconds);
  }
}
