package com.paddleup.rooms.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.rooms.dto.HandoverDto;
import com.paddleup.rooms.dto.HandoverRequest;
import com.paddleup.rooms.dto.RoomState;
import com.paddleup.rooms.exception.RoomConflictException;
import com.paddleup.rooms.exception.RoomsRequestException;
import com.paddleup.rooms.model.Franchise;
import com.paddleup.rooms.model.LogKind;
import com.paddleup.rooms.repository.FranchiseRepository;
import com.paddleup.rooms.repository.RoomLogRepository;
import com.paddleup.rooms.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handing the Commissioner role to a manager. A room always has exactly one Commissioner because
 * the role is one column of the room row.
 */
@Service
public class CommissionerService {

  private final MembershipGuard guard;
  private final RoomRepository rooms;
  private final FranchiseRepository franchises;
  private final RoomLogRepository log;
  private final RoomViews views;
  private final Clock clock;

  CommissionerService(
      MembershipGuard guard,
      RoomRepository rooms,
      FranchiseRepository franchises,
      RoomLogRepository log,
      RoomViews views,
      Clock clock) {
    this.guard = guard;
    this.rooms = rooms;
    this.franchises = franchises;
    this.log = log;
    this.views = views;
    this.clock = clock;
  }

  /**
   * Makes the owner of the franchise the Commissioner, immediately. The old Commissioner stays as a
   * manager if they own a franchise and otherwise leaves the room. Not possible once the room is
   * completed.
   */
  @Transactional
  public HandoverDto handover(UserId caller, UUID roomId, HandoverRequest request) {
    var access = guard.commissioner(roomId, caller);
    if (access.room().state() == RoomState.COMPLETED) {
      throw new RoomConflictException(
          RoomConflictException.ILLEGAL_STATE, "A completed room cannot change Commissioner");
    }
    if (request == null || request.franchiseId() == null) {
      throw new RoomsRequestException("franchiseId", "Choose a manager with a franchise");
    }
    Franchise target =
        franchises
            .findById(request.franchiseId())
            .filter(f -> f.roomId().equals(roomId))
            .orElseThrow(
                () ->
                    new RoomsRequestException("franchiseId", "That franchise is not in this room"));
    if (target.ownerUserId().equals(caller.value())) {
      throw new RoomsRequestException("franchiseId", "You are already the Commissioner");
    }
    Instant now = clock.instant();
    rooms.updateCommissioner(roomId, target.ownerUserId(), now);
    log.append(
        roomId,
        LogKind.COMMISSIONER_HANDOVER,
        caller.value(),
        Map.of(
            "from", caller.value().toString(),
            "to", target.ownerUserId().toString(),
            "franchiseId", target.id().toString()),
        now);
    return new HandoverDto(views.user(target.ownerUserId()), access.franchise().isPresent());
  }
}
