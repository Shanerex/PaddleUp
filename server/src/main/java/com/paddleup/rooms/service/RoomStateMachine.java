package com.paddleup.rooms.service;

import com.paddleup.rooms.dto.RoomState;
import java.util.List;
import java.util.Optional;

/**
 * The room lifecycle as one pure table: what each state allows, and where it may go next. States
 * only move forward; {@code retention} may be skipped. Leaving {@code setup} needs enough managers.
 */
final class RoomStateMachine {

  private RoomStateMachine() {}

  /** Join, config, franchise edits and manager removal share one window. */
  static boolean preAuction(RoomState state) {
    return state == RoomState.SETUP
        || state == RoomState.RETENTION
        || state == RoomState.AUCTION_SCHEDULED;
  }

  static boolean allowsHandover(RoomState state) {
    return state != RoomState.COMPLETED;
  }

  /** The states a room may move to from {@code from}, in order. */
  static List<RoomState> next(RoomState from) {
    return switch (from) {
      case SETUP -> List.of(RoomState.RETENTION, RoomState.AUCTION_SCHEDULED);
      case RETENTION -> List.of(RoomState.AUCTION_SCHEDULED);
      case AUCTION_SCHEDULED -> List.of(RoomState.AUCTION_LIVE);
      case AUCTION_LIVE -> List.of(RoomState.SEASON_LIVE);
      case SEASON_LIVE -> List.of(RoomState.COMPLETED);
      case COMPLETED -> List.of();
    };
  }

  /** What the Commissioner may trigger through REST: only moves that end before the auction. */
  static List<RoomState> nextOverRest(RoomState from) {
    return next(from).stream()
        .filter(s -> s == RoomState.RETENTION || s == RoomState.AUCTION_SCHEDULED)
        .toList();
  }

  /** Why {@code from} to {@code to} is refused, or empty when it is allowed. */
  static Optional<String> refusal(RoomState from, RoomState to, int franchises, int minManagers) {
    if (!next(from).contains(to)) {
      return Optional.of("A room cannot move from " + from.value() + " to " + to.value());
    }
    if (from == RoomState.SETUP && franchises < minManagers) {
      return Optional.of(
          "Needs at least " + minManagers + " franchises to leave setup; has " + franchises);
    }
    return Optional.empty();
  }
}
