package com.paddleup.scoring.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.packs.dto.StatDto;
import com.paddleup.packs.service.PackService;
import com.paddleup.scoring.dto.SheetFieldDto;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AC-1: the sheet fields equal the pack's stat schema, for any pack. */
class SheetFieldsTest {

  private static List<StatDto> stats(Path dir, String packId) {
    var holder = new List[1];
    com.paddleup.packs.PackTestSupport.runner(dir)
        .run(ctx -> holder[0] = ctx.getBean(PackService.class).get(packId).statSchema());
    @SuppressWarnings("unchecked")
    List<StatDto> result = holder[0];
    return result;
  }

  @Test
  void theStubFootballPackGivesOneFieldPerStatAndPerEventType() {
    List<StatDto> schema = stats(com.paddleup.packs.PackTestSupport.STUB_DIR, "stub-football");

    List<SheetFieldDto> fields = SheetFields.of(schema);

    // goals declares two event types, the other five stats none
    assertThat(fields).hasSize(7);
    assertThat(fields)
        .extracting(SheetFieldDto::stat, SheetFieldDto::eventType)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("goals", "open-play"),
            org.assertj.core.groups.Tuple.tuple("goals", "penalty"),
            org.assertj.core.groups.Tuple.tuple("assists", ""),
            org.assertj.core.groups.Tuple.tuple("saves", ""),
            org.assertj.core.groups.Tuple.tuple("passes-completed", ""),
            org.assertj.core.groups.Tuple.tuple("passes-attempted", ""),
            org.assertj.core.groups.Tuple.tuple("minutes", ""));
    assertThat(fields).allSatisfy(f -> assertThat(f.input()).isEqualTo("number"));
    assertThat(fields.get(2).label()).isEqualTo("Assists");
    assertThat(fields.get(2).min()).isEqualByComparingTo("0");
    assertThat(fields.get(2).max()).isEqualByComparingTo("1000000");
  }

  @Test
  void theCricketPackFieldsMatchItsSchemaAndStayCounts() {
    var cricket = Path.of("../packs").toAbsolutePath().normalize();
    List<StatDto> schema = stats(cricket, "cricket-t20");

    List<SheetFieldDto> fields = SheetFields.of(schema);

    long typed = schema.stream().mapToLong(s -> Math.max(1, s.eventTypes().size())).sum();
    assertThat(fields).hasSize((int) typed);
    assertThat(fields).allSatisfy(f -> assertThat(f.input()).isEqualTo("number"));
    assertThat(fields).extracting(SheetFieldDto::kind).contains("integer", "decimal");
  }

  @Test
  void aWholeStatLimitedToOneIsYesNoAndADecimalOneIsNot() {
    var flag =
        new StatDto("f", "Flag", "integer", List.of(), null, null, BigDecimal.ZERO, BigDecimal.ONE);
    var decimal =
        new StatDto("d", "Dec", "decimal", List.of(), null, null, BigDecimal.ZERO, BigDecimal.ONE);
    var capped =
        new StatDto("c", "Cap", "integer", List.of(), null, null, BigDecimal.ZERO, BigDecimal.TEN);
    var typed =
        new StatDto(
            "t",
            "Typed",
            "integer",
            List.of("x", "y"),
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ONE);

    var fields = SheetFields.of(List.of(flag, decimal, capped, typed));

    assertThat(fields)
        .extracting(SheetFieldDto::stat, SheetFieldDto::input)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("f", "yes_no"),
            org.assertj.core.groups.Tuple.tuple("d", "number"),
            org.assertj.core.groups.Tuple.tuple("c", "number"),
            org.assertj.core.groups.Tuple.tuple("t", "yes_no"),
            org.assertj.core.groups.Tuple.tuple("t", "yes_no"));
    assertThat(fields.get(0).max()).isEqualByComparingTo("1");
  }
}
