package com.paddleup.scoring.util;

import com.paddleup.packs.dto.StatDto;
import com.paddleup.scoring.dto.SheetFieldDto;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the entry fields of a stat sheet from the room's stat schema: one field per stat, or one
 * per event type for a stat that declares them. A whole-number stat limited to 0 or 1 is a yes/no
 * field; every other field is a number. Stateless.
 */
public final class SheetFields {

  public static final String NUMBER = "number";
  public static final String YES_NO = "yes_no";

  private SheetFields() {}

  public static List<SheetFieldDto> of(List<StatDto> stats) {
    List<SheetFieldDto> fields = new ArrayList<>();
    for (StatDto stat : stats) {
      if (stat.eventTypes().isEmpty()) {
        fields.add(field(stat, ""));
      } else {
        stat.eventTypes().forEach(type -> fields.add(field(stat, type)));
      }
    }
    return fields;
  }

  private static SheetFieldDto field(StatDto stat, String eventType) {
    var min = StatValues.min(stat);
    var max = StatValues.max(stat);
    boolean flag =
        stat.whole() && min.signum() == 0 && max.compareTo(java.math.BigDecimal.ONE) == 0;
    return new SheetFieldDto(
        stat.id(),
        eventType,
        stat.label(),
        stat.kind(),
        stat.unit(),
        stat.group(),
        min,
        max,
        flag ? YES_NO : NUMBER,
        null,
        null);
  }
}
