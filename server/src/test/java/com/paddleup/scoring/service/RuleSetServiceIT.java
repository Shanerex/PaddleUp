package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.rooms.exception.RoomForbiddenException;
import com.paddleup.rooms.exception.RoomNotFoundException;
import com.paddleup.scoring.ScoringIT;
import com.paddleup.scoring.dto.RuleSetViewDto;
import com.paddleup.scoring.exception.InvalidRuleSetException;
import com.paddleup.scoring.exception.RuleSetConflictException;
import com.paddleup.scoring.model.RuleLogKind;
import com.paddleup.scoring.repository.RuleLogRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** AC-10 to AC-16: reading, editing, reverting and changing the rules after the freeze. */
class RuleSetServiceIT extends ScoringIT {

  @Autowired RuleSetService rules;
  @Autowired RuleLogRepository logs;

  private RuleSetDto defaults() {
    return port.ruleInputs(roomId).defaultRules();
  }

  private String code(Runnable call) {
    var thrown = catchRuleConflict(call);
    return thrown.code();
  }

  private static RuleSetConflictException catchRuleConflict(Runnable call) {
    try {
      call.run();
    } catch (RuleSetConflictException e) {
      return e;
    }
    throw new AssertionError("expected a RuleSetConflictException");
  }

  // ---- read (T-22) ----

  @Test
  void aRoomWithNoRowReadsAsThePackDefaultAtVersionZero() {
    var view = rules.read(roomId, user(commissioner));

    assertThat(view.status()).isEqualTo(RuleSetViewDto.PACK_DEFAULT);
    assertThat(view.version()).isZero();
    assertThat(view.rules()).isEqualTo(defaults());
    assertThat(view.packVersion()).isEqualTo("1.1.0");
    assertThat(view.frozen()).isFalse();
    assertThat(view.freezesAt()).isEqualTo(START);
    assertThat(version(roomId)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM scoring_rule_sets WHERE room_id = ?", Integer.class, roomId))
        .isZero();
  }

  @Test
  void aManagerReadsTheSameViewAndANonMemberIsNotFound() {
    assertThat(rules.read(roomId, user(manager))).isEqualTo(rules.read(roomId, user(commissioner)));

    assertThatThrownBy(() -> rules.read(roomId, user(person())))
        .isInstanceOf(RoomNotFoundException.class);
  }

  @Test
  void aRoomWithNoMatchHasNoFreezeInstantAndIsNeverFrozen() {
    jdbc.update("DELETE FROM scoring_matches WHERE dataset_id = ?", datasetId);
    clock.freeze(START.plus(Duration.ofDays(900)));

    var view = rules.read(roomId, user(commissioner));

    assertThat(view.freezesAt()).isNull();
    assertThat(view.frozen()).isFalse();
  }

  @Test
  void aScoredRoomStaysFrozenWhenTheFirstMatchIsMovedLater() {
    clock.freeze(START.plusSeconds(60));
    // Provider stats recompute the room, which records the freeze with no rule write at all.
    provider(datasetId, "m1", player.sourcePlayerId(), "10");
    matches.upsertMatch(datasetId, "m1", START.plus(Duration.ofDays(1)));

    var view = rules.read(roomId, user(manager));

    assertThat(view.frozen()).isTrue();
    assertThat(view.freezesAt()).isEqualTo(START);
    assertThat(code(() -> rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), "2"))))
        .isEqualTo("frozen");
  }

  @Test
  void aMatchThatStartedBeforeTheRoomWasCreatedDoesNotFreezeIt() {
    var created =
        jdbc.queryForObject(
                "SELECT created_at FROM rooms WHERE id = ?", java.sql.Timestamp.class, roomId)
            .toInstant();
    matches.upsertMatch(datasetId, "m0", created.minus(Duration.ofDays(1)));

    var view = rules.read(roomId, user(commissioner));

    assertThat(view.freezesAt()).isEqualTo(START);
    assertThat(view.frozen()).isFalse();
    assertThat(rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), "2")).status())
        .isEqualTo(RuleSetViewDto.CUSTOM);
  }

  @Test
  void theViewIsFrozenFromTheStartInstantEvenBeforeAnyWrite() {
    clock.freeze(START);

    assertThat(rules.read(roomId, user(manager)).frozen()).isTrue();
  }

  // ---- edit (T-23) ----

  @Test
  void aCommissionerEditOneSecondBeforeTheStartIsAcceptedAndMakesTheSetCustom() {
    clock.freeze(START.minusSeconds(1));
    var edited = runsWorth(defaults(), "2");

    var view = rules.edit(roomId, user(commissioner), 0, edited);

    assertThat(view.status()).isEqualTo(RuleSetViewDto.CUSTOM);
    assertThat(view.version()).isEqualTo(1);
    assertThat(view.rules()).isEqualTo(edited);
    assertThat(rules.read(roomId, user(manager)).rules()).isEqualTo(edited);
    var entries = logs.list(roomId);
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).kind()).isEqualTo(RuleLogKind.EDIT);
    assertThat(entries.get(0).diff().changed()).containsExactly("runs-points");
    assertThat(entries.get(0).before()).isEqualTo(defaults());
  }

  @Test
  void anEditAtTheStartInstantIs409FrozenAndRecordsTheFreeze() {
    clock.freeze(START);

    assertThat(code(() -> rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), "2"))))
        .isEqualTo("frozen");

    assertThat(logCount(roomId)).isZero();
    var frozenAt =
        jdbc.queryForObject(
            "SELECT frozen_at FROM scoring_rule_sets WHERE room_id = ?",
            java.time.OffsetDateTime.class,
            roomId);
    assertThat(frozenAt.toInstant()).isEqualTo(START);
    assertThat(rules.read(roomId, user(commissioner)).status())
        .isEqualTo(RuleSetViewDto.PACK_DEFAULT);
  }

  @Test
  void aManagerAndANonMemberCannotEdit() {
    assertThatThrownBy(() -> rules.edit(roomId, user(manager), 0, defaults()))
        .isInstanceOf(RoomForbiddenException.class);
    assertThatThrownBy(() -> rules.edit(roomId, user(person()), 0, defaults()))
        .isInstanceOf(RoomNotFoundException.class);
    assertThat(version(roomId)).isZero();
  }

  @Test
  void anInvalidSetSavesNothing() {
    var bad = new RuleSetDto(List.of(new RuleDto.PerUnit("x", "nope", BigDecimal.ONE)));

    assertThatThrownBy(() -> rules.edit(roomId, user(commissioner), 0, bad))
        .isInstanceOfSatisfying(
            InvalidRuleSetException.class,
            e -> assertThat(e.violations()).extracting("field").containsExactly("rules[0].stat"));

    assertThat(version(roomId)).isZero();
    assertThat(logCount(roomId)).isZero();
    assertThat(rules.read(roomId, user(commissioner)).status())
        .isEqualTo(RuleSetViewDto.PACK_DEFAULT);
  }

  @Test
  void aStaleVersionIs409() {
    rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), "2"));

    assertThat(code(() -> rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), "3"))))
        .isEqualTo("stale_version");
    assertThat(logCount(roomId)).isEqualTo(1);
  }

  @Test
  void anEditRecomputesTheRoomsScoredMatches() {
    provider(datasetId, "m1", player.sourcePlayerId(), "30");
    assertThat(scoring.points(roomId, "m1").get(0).total()).isEqualByComparingTo("34");

    rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), "2"));

    var points = scoring.points(roomId, "m1").get(0);
    assertThat(points.total()).isEqualByComparingTo("64");
    assertThat(points.ruleSetVersion()).isEqualTo(1);
  }

  // ---- revert (T-24) ----

  @Test
  void revertRestoresTheSnapshotExactlyAndIsLogged() {
    rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), "2"));

    var view = rules.revert(roomId, user(commissioner), 1);

    assertThat(view.status()).isEqualTo(RuleSetViewDto.PACK_DEFAULT);
    assertThat(view.rules()).isEqualTo(defaults());
    assertThat(view.version()).isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                "SELECT rules FROM scoring_rule_sets WHERE room_id = ?", String.class, roomId))
        .containsOnlyNulls();
    var entries = logs.list(roomId);
    assertThat(entries).extracting("kind").containsExactly(RuleLogKind.EDIT, RuleLogKind.REVERT);
    assertThat(entries.get(1).after()).isEqualTo(defaults());
  }

  @Test
  void revertIsRefusedAfterTheFreezeAndByAManager() {
    assertThatThrownBy(() -> rules.revert(roomId, user(manager), 0))
        .isInstanceOf(RoomForbiddenException.class);
    clock.freeze(START);

    assertThat(code(() -> rules.revert(roomId, user(commissioner), 0))).isEqualTo("frozen");
  }

  @Test
  void revertRecomputesTheRoom() {
    provider(datasetId, "m1", player.sourcePlayerId(), "30");
    rules.edit(roomId, user(commissioner), 0, runsWorth(defaults(), "2"));

    rules.revert(roomId, user(commissioner), 1);

    assertThat(scoring.points(roomId, "m1").get(0).total()).isEqualByComparingTo("34");
  }

  // ---- post-freeze change (T-25, T-26) ----

  private void freezeNow() {
    clock.freeze(START.plusSeconds(60));
  }

  @Test
  void aPostFreezeChangeBeforeTheFreezeIs409NotFrozen() {
    assertThat(
            code(
                () ->
                    rules.changeAfterFreeze(
                        roomId,
                        user(commissioner),
                        0,
                        runsWorth(defaults(), "2"),
                        "Because",
                        true)))
        .isEqualTo("not_frozen");
    assertThat(logCount(roomId)).isZero();
  }

  @Test
  void aPostFreezeChangeNeedsAReasonAndTheAttestation() {
    freezeNow();
    var set = runsWorth(defaults(), "2");

    for (var args :
        List.of(
            new Object[] {null, true},
            new Object[] {"   ", true},
            new Object[] {"<b></b>", true},
            new Object[] {"x".repeat(501), true},
            new Object[] {"Fine", false})) {
      assertThatThrownBy(
              () ->
                  rules.changeAfterFreeze(
                      roomId, user(commissioner), 0, set, (String) args[0], (Boolean) args[1]))
          .isInstanceOf(InvalidRuleSetException.class);
    }

    assertThatThrownBy(
            () -> rules.changeAfterFreeze(roomId, user(commissioner), 0, set, null, false))
        .isInstanceOfSatisfying(
            InvalidRuleSetException.class,
            e ->
                assertThat(e.violations())
                    .extracting("field")
                    .containsExactly("reason", "attested"));
    assertThat(logCount(roomId)).isZero();
    assertThat(version(roomId)).isZero();
  }

  @Test
  void aPostFreezeChangeWithBothSavesOneLogEntryWithTheDiff() {
    freezeNow();
    var set = runsWorth(defaults(), "2");

    var view =
        rules.changeAfterFreeze(
            roomId, user(commissioner), 0, set, "  Agreed <i>in</i> the group chat ", true);

    assertThat(view.status()).isEqualTo(RuleSetViewDto.CUSTOM);
    assertThat(view.version()).isEqualTo(1);
    assertThat(view.frozen()).isTrue();
    var entries = logs.list(roomId);
    assertThat(entries).hasSize(1);
    var entry = entries.get(0);
    assertThat(entry.kind()).isEqualTo(RuleLogKind.POST_FREEZE_CHANGE);
    assertThat(entry.actor()).isEqualTo(userId(commissioner));
    assertThat(entry.occurredAt()).isEqualTo(START.plusSeconds(60));
    assertThat(entry.reason()).isEqualTo("Agreed in the group chat");
    assertThat(entry.attested()).isTrue();
    assertThat(entry.before()).isEqualTo(defaults());
    assertThat(entry.after()).isEqualTo(set);
    assertThat(entry.diff().changed()).containsExactly("runs-points");
  }

  @Test
  void aPostFreezeChangeRecomputesEveryScoredMatchOfThisRoomOnly() {
    matches.upsertMatch(datasetId, "m2", START.plus(Duration.ofDays(1)));
    UUID other = otherRoom();
    var otherPlayer =
        port.players(other).stream()
            .filter(p -> p.sourcePlayerId().equals(player.sourcePlayerId()))
            .findFirst()
            .orElseThrow();
    provider(datasetId, "m1", player.sourcePlayerId(), "30");
    provider(datasetId, "m2", player.sourcePlayerId(), "10");
    assertThat(scoring.points(roomId, "m1").get(0).total()).isEqualByComparingTo("34");
    assertThat(scoring.points(roomId, "m2").get(0).total()).isEqualByComparingTo("14");
    freezeNow();

    rules.changeAfterFreeze(
        roomId, user(commissioner), 0, runsWorth(defaults(), "2"), "Doubling runs", true);

    assertThat(scoring.points(roomId, "m1").get(0).total()).isEqualByComparingTo("64");
    assertThat(scoring.points(roomId, "m2").get(0).total()).isEqualByComparingTo("24");
    assertThat(scoring.points(other, "m1").get(0).total()).isEqualByComparingTo("34");
    assertThat(scoring.points(other, "m1").get(0).poolPlayerId())
        .isEqualTo(otherPlayer.poolPlayerId());
    assertThat(scoring.points(other, "m2").get(0).total()).isEqualByComparingTo("14");
    // The old set stays in the log.
    assertThat(logs.list(roomId).get(0).before()).isEqualTo(defaults());
    assertThat(logCount(other)).isZero();
  }

  @Test
  void aSecondPostFreezeChangeKeepsBothOldSetsInTheLog() {
    freezeNow();
    var first = runsWorth(defaults(), "2");
    var second = runsWorth(defaults(), "3");
    rules.changeAfterFreeze(roomId, user(commissioner), 0, first, "One", true);

    rules.changeAfterFreeze(roomId, user(commissioner), 1, second, "Two", true);

    var entries = logs.list(roomId);
    assertThat(entries).hasSize(2);
    assertThat(entries.get(0).after()).isEqualTo(first);
    assertThat(entries.get(1).before()).isEqualTo(first);
    assertThat(entries.get(1).after()).isEqualTo(second);
  }

  @Test
  void anOrdinaryEditAfterAPostFreezeChangeIsStillFrozen() {
    freezeNow();
    rules.changeAfterFreeze(roomId, user(commissioner), 0, runsWorth(defaults(), "2"), "One", true);

    assertThat(code(() -> rules.edit(roomId, user(commissioner), 1, runsWorth(defaults(), "3"))))
        .isEqualTo("frozen");
  }
}
