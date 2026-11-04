package com.paddleup.rooms.service;

import static com.paddleup.rooms.dto.RoomState.AUCTION_LIVE;
import static com.paddleup.rooms.dto.RoomState.AUCTION_SCHEDULED;
import static com.paddleup.rooms.dto.RoomState.COMPLETED;
import static com.paddleup.rooms.dto.RoomState.RETENTION;
import static com.paddleup.rooms.dto.RoomState.SEASON_LIVE;
import static com.paddleup.rooms.dto.RoomState.SETUP;
import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.rooms.dto.RoomState;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** The requirements matrix, row by row. */
class RoomStateMachineTest {

  /** state, join/config/franchise/remove allowed, handover allowed, next states. */
  static Stream<Arguments> matrix() {
    return Stream.of(
        Arguments.of(SETUP, true, true, List.of(RETENTION, AUCTION_SCHEDULED)),
        Arguments.of(RETENTION, true, true, List.of(AUCTION_SCHEDULED)),
        Arguments.of(AUCTION_SCHEDULED, true, true, List.of(AUCTION_LIVE)),
        Arguments.of(AUCTION_LIVE, false, true, List.of(SEASON_LIVE)),
        Arguments.of(SEASON_LIVE, false, true, List.of(COMPLETED)),
        Arguments.of(COMPLETED, false, false, List.of()));
  }

  @ParameterizedTest
  @MethodSource("matrix")
  void eachStateAllowsWhatTheMatrixSays(
      RoomState state, boolean preAuction, boolean handover, List<RoomState> next) {
    assertThat(RoomStateMachine.preAuction(state)).isEqualTo(preAuction);
    assertThat(RoomStateMachine.allowsHandover(state)).isEqualTo(handover);
    assertThat(RoomStateMachine.next(state)).isEqualTo(next);
  }

  @ParameterizedTest
  @EnumSource(RoomState.class)
  void onlyForwardMovesAreLegalAndNoStateReachesItself(RoomState from) {
    for (RoomState to : RoomState.values()) {
      boolean legal = RoomStateMachine.refusal(from, to, 10, 2).isEmpty();
      if (legal) {
        assertThat(to.ordinal()).isGreaterThan(from.ordinal());
      }
    }
    assertThat(RoomStateMachine.refusal(from, from, 10, 2)).isPresent();
  }

  @Test
  void retentionCanBeSkippedButNothingElse() {
    assertThat(RoomStateMachine.refusal(SETUP, AUCTION_SCHEDULED, 2, 2)).isEmpty();
    assertThat(RoomStateMachine.refusal(SETUP, AUCTION_LIVE, 2, 2)).isPresent();
    assertThat(RoomStateMachine.refusal(SETUP, SEASON_LIVE, 2, 2)).isPresent();
    assertThat(RoomStateMachine.refusal(RETENTION, AUCTION_LIVE, 2, 2)).isPresent();
    assertThat(RoomStateMachine.refusal(AUCTION_SCHEDULED, SEASON_LIVE, 2, 2)).isPresent();
  }

  @Test
  void movingBackwardIsRefusedFromEveryState() {
    assertThat(RoomStateMachine.refusal(RETENTION, SETUP, 5, 2)).isPresent();
    assertThat(RoomStateMachine.refusal(AUCTION_LIVE, AUCTION_SCHEDULED, 5, 2)).isPresent();
    assertThat(RoomStateMachine.refusal(COMPLETED, SEASON_LIVE, 5, 2)).isPresent();
  }

  @Test
  void completedHasNowhereToGo() {
    for (RoomState to : RoomState.values()) {
      assertThat(RoomStateMachine.refusal(COMPLETED, to, 5, 2)).isPresent();
    }
  }

  @Test
  void leavingSetupNeedsAtLeastTheMinimumNumberOfFranchises() {
    assertThat(RoomStateMachine.refusal(SETUP, RETENTION, 1, 2)).isPresent();
    assertThat(RoomStateMachine.refusal(SETUP, AUCTION_SCHEDULED, 0, 2)).isPresent();
    assertThat(RoomStateMachine.refusal(SETUP, RETENTION, 2, 2)).isEmpty();
    assertThat(RoomStateMachine.refusal(SETUP, AUCTION_SCHEDULED, 7, 3)).isEmpty();
  }

  @Test
  void theMinimumGatesEveryPreAuctionState() {
    assertThat(RoomStateMachine.refusal(RETENTION, AUCTION_SCHEDULED, 1, 2)).isPresent();
    assertThat(RoomStateMachine.refusal(AUCTION_SCHEDULED, AUCTION_LIVE, 1, 2)).isPresent();
    assertThat(RoomStateMachine.refusal(RETENTION, AUCTION_SCHEDULED, 2, 2)).isEmpty();
    assertThat(RoomStateMachine.refusal(AUCTION_SCHEDULED, AUCTION_LIVE, 2, 2)).isEmpty();
  }

  @Test
  void theMinimumDoesNotGateLaterStates() {
    assertThat(RoomStateMachine.refusal(AUCTION_LIVE, SEASON_LIVE, 0, 2)).isEmpty();
    assertThat(RoomStateMachine.refusal(SEASON_LIVE, COMPLETED, 0, 2)).isEmpty();
  }

  @Test
  void overRestOnlyTheTwoPreAuctionMovesAreOffered() {
    assertThat(RoomStateMachine.nextOverRest(SETUP)).containsExactly(RETENTION, AUCTION_SCHEDULED);
    assertThat(RoomStateMachine.nextOverRest(RETENTION)).containsExactly(AUCTION_SCHEDULED);
    assertThat(RoomStateMachine.nextOverRest(AUCTION_SCHEDULED)).isEmpty();
    assertThat(RoomStateMachine.nextOverRest(AUCTION_LIVE)).isEmpty();
    assertThat(RoomStateMachine.nextOverRest(SEASON_LIVE)).isEmpty();
    assertThat(RoomStateMachine.nextOverRest(COMPLETED)).isEmpty();
  }

  @Test
  void stateNamesUseTheWireForm() {
    assertThat(AUCTION_LIVE.value()).isEqualTo("auction_live");
    assertThat(RoomState.fromValue("season_live")).isEqualTo(SEASON_LIVE);
  }
}
