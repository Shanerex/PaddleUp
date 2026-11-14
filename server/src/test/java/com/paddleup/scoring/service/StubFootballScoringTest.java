package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.RuleDto;
import com.paddleup.rooms.RoomsTestSupport;
import com.paddleup.scoring.StatLines;
import com.paddleup.scoring.dto.PointsLineDto;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AC-1: a second sport's rule set, all six primitives, scores with zero core changes. */
class StubFootballScoringTest {

  static final PackDto STUB =
      RoomsTestSupport.load(
          Path.of("src/test/resources/packs/stub").toAbsolutePath(), "stub-football");

  @Test
  void theStubRuleSetUsesAllSixPrimitives() {
    var types =
        STUB.defaultRuleSet().rules().stream()
            .map(r -> r.getClass().getSimpleName())
            .distinct()
            .toList();

    assertThat(types)
        .containsExactlyInAnyOrder(
            RuleDto.PerUnit.class.getSimpleName(),
            RuleDto.ThresholdBonus.class.getSimpleName(),
            RuleDto.BandedRate.class.getSimpleName(),
            RuleDto.RoleConditional.class.getSimpleName(),
            RuleDto.EventTypeBonus.class.getSimpleName(),
            RuleDto.AppearanceBonus.class.getSimpleName());
  }

  private static com.paddleup.scoring.dto.EvaluationDto score(String role) {
    var line =
        StatLines.of()
            .stat("assists", 1)
            .stat("saves", 9)
            .stat("passes-completed", 38)
            .stat("passes-attempted", 40)
            .stat("goals", 1)
            .event("goals", "penalty", 1)
            .stat("minutes", 90)
            .build();
    return PointsEvaluator.evaluate(
        STUB.defaultRuleSet(), STUB.appearanceRule(), STUB.statSchema(), role, line);
  }

  @Test
  void aKeeperScoresEverySixPrimitiveWithLabelsFromTheStatSchema() {
    var result = score("gk");

    assertThat(result.total()).isEqualByComparingTo("25.5");
    assertThat(result.lines())
        .containsExactly(
            new PointsLineDto("assist-points", "Assists", new BigDecimal("3")),
            new PointsLineDto("save-bonus", "Saves", new BigDecimal("6.5")),
            new PointsLineDto("pass-accuracy", "Passes completed", new BigDecimal("2")),
            new PointsLineDto("keeper-saves", "Saves", new BigDecimal("9")),
            new PointsLineDto("penalty-goal", "Goals", new BigDecimal("4")),
            new PointsLineDto("appearance", "appearance", new BigDecimal("1")));
  }

  @Test
  void aDefenderWithTheSameStatsMissesTheRoleConditionalRule() {
    var result = score("def");

    assertThat(result.total()).isEqualByComparingTo("16.5");
    assertThat(result.lines())
        .extracting(PointsLineDto::ruleId)
        .containsExactly(
            "assist-points", "save-bonus", "pass-accuracy", "penalty-goal", "appearance");
  }

  @Test
  void passAccuracyNeedsTwentyAttemptsAndTheBandEdgesTakeTheHigherBand() {
    java.util.function.BiFunction<Integer, Integer, List<PointsLineDto>> lines =
        (done, tried) ->
            PointsEvaluator.evaluate(
                    STUB.defaultRuleSet(),
                    STUB.appearanceRule(),
                    STUB.statSchema(),
                    "mid",
                    StatLines.of()
                        .stat("passes-completed", done)
                        .stat("passes-attempted", tried)
                        .build())
                .lines();

    assertThat(lines.apply(19, 19)).isEmpty();
    assertThat(lines.apply(17, 20)).isEmpty();
    assertThat(lines.apply(12, 20)).isEmpty();
    assertThat(lines.apply(18, 20))
        .extracting(PointsLineDto::points)
        .containsExactly(new BigDecimal("2"));
    assertThat(lines.apply(11, 20))
        .extracting(PointsLineDto::ruleId, PointsLineDto::points)
        .containsExactly(tuple("pass-accuracy", new BigDecimal("-1")));
    assertThat(lines.apply(20, 20))
        .extracting(PointsLineDto::points)
        .containsExactly(new BigDecimal("2"));
  }
}
