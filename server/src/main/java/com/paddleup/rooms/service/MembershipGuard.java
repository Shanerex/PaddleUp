package com.paddleup.rooms.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.rooms.exception.RoomForbiddenException;
import com.paddleup.rooms.exception.RoomNotFoundException;
import com.paddleup.rooms.model.Franchise;
import com.paddleup.rooms.model.Room;
import com.paddleup.rooms.repository.FranchiseRepository;
import com.paddleup.rooms.repository.RoomRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Who may see and change a room. A member is the Commissioner or a franchise owner; everyone else
 * gets 404, so a room id reveals nothing. A member who is not the Commissioner gets 403 on
 * Commissioner actions. Mutating callers use the locking forms inside their transaction.
 */
@Component
class MembershipGuard {

  /** The room as the caller may see it. */
  record Access(Room room, boolean commissioner, Optional<Franchise> franchise) {
    String role() {
      return commissioner ? "commissioner" : "manager";
    }
  }

  private final RoomRepository rooms;
  private final FranchiseRepository franchises;

  MembershipGuard(RoomRepository rooms, FranchiseRepository franchises) {
    this.rooms = rooms;
    this.franchises = franchises;
  }

  /** Any member, without a lock. */
  Access member(UUID roomId, UserId user) {
    return access(rooms.find(roomId).orElseThrow(RoomNotFoundException::new), user);
  }

  /** Any member; the room row stays locked until the transaction ends. */
  Access memberLocked(UUID roomId, UserId user) {
    return access(rooms.findForUpdate(roomId).orElseThrow(RoomNotFoundException::new), user);
  }

  /** The Commissioner only, with the room locked. */
  Access commissioner(UUID roomId, UserId user) {
    Access access = memberLocked(roomId, user);
    if (!access.commissioner()) {
      throw new RoomForbiddenException("Only the Commissioner can do this");
    }
    return access;
  }

  private Access access(Room room, UserId user) {
    boolean commissioner = room.commissionerUserId().equals(user.value());
    Optional<Franchise> franchise = franchises.findByOwner(room.id(), user.value());
    if (!commissioner && franchise.isEmpty()) {
      throw new RoomNotFoundException();
    }
    return new Access(room, commissioner, franchise);
  }
}
