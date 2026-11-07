package com.paddleup.rooms.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.rooms.dto.RoomRef;
import com.paddleup.rooms.dto.RoomState;
import com.paddleup.rooms.dto.TransitionDto;
import com.paddleup.rooms.exception.RoomConflictException;
import com.paddleup.rooms.exception.RoomNotFoundException;
import com.paddleup.rooms.exception.RoomsRequestException;
import com.paddleup.rooms.model.LogKind;
import com.paddleup.rooms.model.Room;
import com.paddleup.rooms.repository.FranchiseRepository;
import com.paddleup.rooms.repository.RoomLogRepository;
import com.paddleup.rooms.repository.RoomPoolRepository;
import com.paddleup.rooms.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Room state changes. The Commissioner moves a room before the auction through REST; every later
 * move is made by another module through {@link #advance}. Both go through the same state machine
 * and leave a log row naming the actor.
 */
@Service
public class RoomLifecycle {

  private final RoomRepository rooms;
  private final FranchiseRepository franchises;
  private final RoomLogRepository log;
  private final MembershipGuard guard;
  private final InviteService invites;
  private final RoomPoolRepository pools;
  private final RoomViews views;
  private final Clock clock;

  RoomLifecycle(
      RoomRepository rooms,
      FranchiseRepository franchises,
      RoomLogRepository log,
      MembershipGuard guard,
      InviteService invites,
      RoomPoolRepository pools,
      RoomViews views,
      Clock clock) {
    this.rooms = rooms;
    this.franchises = franchises;
    this.log = log;
    this.guard = guard;
    this.invites = invites;
    this.pools = pools;
    this.views = views;
    this.clock = clock;
  }

  /**
   * Moves a room to {@code to} on behalf of another module, such as the auction going live. The
   * caller has already decided who may ask; this checks only that the move is legal and records
   * {@code actor}. Throws a 409 {@link RoomConflictException} when it is not.
   */
  @Transactional
  public RoomRef advance(UUID roomId, RoomState to, UserId actor) {
    if (to == null || actor == null) {
      throw new IllegalArgumentException("A target state and an actor are required");
    }
    Room room = rooms.findForUpdate(roomId).orElseThrow(RoomNotFoundException::new);
    return move(room, to, actor);
  }

  /** The Commissioner's move before the auction: to retention or auction_scheduled. */
  @Transactional
  public TransitionDto advanceAsCommissioner(UserId caller, UUID roomId, RoomState to) {
    var access = guard.commissioner(roomId, caller);
    if (to == null) {
      throw new RoomsRequestException("to", "Choose a state");
    }
    Room room = access.room();
    var refusal = refusal(room, to);
    if (refusal != null) {
      throw refusal;
    }
    if (!RoomStateMachine.nextOverRest(room.state()).contains(to)) {
      throw new RoomConflictException(
          RoomConflictException.ILLEGAL_STATE,
          "Only the auction itself can move a room to " + to.value());
    }
    Instant at = apply(room, to, caller);
    return new TransitionDto(room.state(), to, views.user(caller.value()), at);
  }

  /** Every state change of the room so far, oldest first. Any member. */
  @Transactional(readOnly = true)
  public List<TransitionDto> history(UserId caller, UUID roomId) {
    guard.member(roomId, caller);
    return log.byKind(roomId, LogKind.TRANSITION).stream()
        .map(
            e ->
                new TransitionDto(
                    RoomState.fromValue((String) e.detail().get("from")),
                    RoomState.fromValue((String) e.detail().get("to")),
                    e.actorUserId() == null ? null : views.user(e.actorUserId()),
                    e.occurredAt()))
        .toList();
  }

  private RoomRef move(Room room, RoomState to, UserId actor) {
    var refusal = refusal(room, to);
    if (refusal != null) {
      throw refusal;
    }
    apply(room, to, actor);
    return new RoomRef(room.id(), to);
  }

  private RoomConflictException refusal(Room room, RoomState to) {
    var refusal =
        RoomStateMachine.refusal(
            room.state(), to, franchises.count(room.id()), room.config().minManagers());
    if (refusal.isEmpty() && to == RoomState.AUCTION_LIVE) {
      refusal = poolRefusal(room);
    }
    return refusal
        .map(msg -> new RoomConflictException(RoomConflictException.ILLEGAL_STATE, msg))
        .orElse(null);
  }

  /** The auction locks the pool, so it needs enough players to fill every squad to its minimum. */
  private java.util.Optional<String> poolRefusal(Room room) {
    if (!pools.exists(room.id())) {
      return java.util.Optional.of("No player pool yet; load season data first");
    }
    int players = pools.countPlayers(room.id());
    long needed = Math.max(1L, (long) franchises.count(room.id()) * room.config().squadMin());
    if (players < needed) {
      return java.util.Optional.of(
          "The pool has "
              + players
              + (players == 1 ? " player" : " players")
              + " but "
              + needed
              + " are needed to fill every squad; add players first");
    }
    return java.util.Optional.empty();
  }

  private Instant apply(Room room, RoomState to, UserId actor) {
    Instant now = clock.instant();
    rooms.updateState(room.id(), to, now);
    if (to == RoomState.AUCTION_LIVE) {
      invites.revokeForRoom(room.id(), actor.value(), "auction_live");
    }
    log.append(
        room.id(),
        LogKind.TRANSITION,
        actor.value(),
        Map.of("from", room.state().value(), "to", to.value()),
        now);
    return now;
  }
}
