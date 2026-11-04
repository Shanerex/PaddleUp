package com.paddleup.rooms.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.rooms.dto.FranchiseDto;
import com.paddleup.rooms.dto.FranchiseRequest;
import com.paddleup.rooms.dto.MemberDto;
import com.paddleup.rooms.exception.RoomConflictException;
import com.paddleup.rooms.exception.RoomForbiddenException;
import com.paddleup.rooms.exception.RoomNotFoundException;
import com.paddleup.rooms.exception.RoomsRequestException;
import com.paddleup.rooms.model.Franchise;
import com.paddleup.rooms.model.LogKind;
import com.paddleup.rooms.model.Room;
import com.paddleup.rooms.repository.FranchiseRepository;
import com.paddleup.rooms.repository.RoomLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating franchises. The caller holds the room lock and has checked who is acting. */
@Service
public class FranchiseService {

  private final FranchiseRepository franchises;
  private final RoomLogRepository log;
  private final MembershipGuard guard;
  private final RoomViews views;
  private final Clock clock;

  FranchiseService(
      FranchiseRepository franchises,
      RoomLogRepository log,
      MembershipGuard guard,
      RoomViews views,
      Clock clock) {
    this.franchises = franchises;
    this.log = log;
    this.guard = guard;
    this.views = views;
    this.clock = clock;
  }

  /** The Commissioner, then every franchise owner, in the order they joined. */
  @Transactional(readOnly = true)
  public List<MemberDto> members(UserId caller, UUID roomId) {
    return membersOf(guard.member(roomId, caller).room());
  }

  private List<MemberDto> membersOf(Room room) {
    List<Franchise> all = franchises.findByRoom(room.id());
    var out = new ArrayList<MemberDto>();
    Franchise own =
        all.stream()
            .filter(f -> f.ownerUserId().equals(room.commissionerUserId()))
            .findFirst()
            .orElse(null);
    out.add(views.member(room.commissionerUserId(), "commissioner", own));
    for (Franchise f : all) {
      if (f != own) {
        out.add(views.member(f.ownerUserId(), "manager", f));
      }
    }
    return out;
  }

  /**
   * The Commissioner's own franchise, without a code. It follows the same rules as joining and
   * counts toward the manager limits; a Commissioner can have one at most.
   */
  @Transactional
  public FranchiseDto createOwn(UserId caller, UUID roomId, FranchiseRequest request) {
    var access = guard.commissioner(roomId, caller);
    if (access.franchise().isPresent()) {
      throw new RoomConflictException(
          RoomConflictException.DUPLICATE, "You already have a franchise in this room");
    }
    return toDto(create(access.room(), caller.value(), request, LogKind.MANAGER_JOINED));
  }

  /**
   * Removes another manager's franchise, which frees the slot. The Commissioner only, and only
   * before the auction is live. Returns the members who remain.
   */
  @Transactional
  public List<MemberDto> remove(UserId caller, UUID roomId, UUID franchiseId) {
    var access = guard.commissioner(roomId, caller);
    Franchise franchise =
        franchises
            .findById(franchiseId)
            .filter(f -> f.roomId().equals(roomId))
            .orElseThrow(RoomNotFoundException::new);
    if (franchise.ownerUserId().equals(caller.value())) {
      throw new RoomsRequestException("franchiseId", "You cannot remove your own franchise");
    }
    if (!RoomStateMachine.preAuction(access.room().state())) {
      throw new RoomConflictException(
          RoomConflictException.ROOM_LOCKED, "Managers cannot be removed once the auction is live");
    }
    franchises.delete(franchise.id());
    log.append(
        roomId,
        LogKind.MANAGER_REMOVED,
        caller.value(),
        Map.of(
            "franchiseId", franchise.id().toString(),
            "removedUserId", franchise.ownerUserId().toString(),
            "name", franchise.name()),
        clock.instant());
    return membersOf(access.room());
  }

  /**
   * Changes the caller's own franchise name, colour or monogram; a null field stays as it is. Only
   * the owner may, and only before the auction is live.
   */
  @Transactional
  public FranchiseDto edit(UserId caller, UUID roomId, UUID franchiseId, FranchiseRequest request) {
    var access = guard.memberLocked(roomId, caller);
    Franchise franchise =
        franchises
            .findById(franchiseId)
            .filter(f -> f.roomId().equals(roomId))
            .orElseThrow(RoomNotFoundException::new);
    if (!franchise.ownerUserId().equals(caller.value())) {
      throw new RoomForbiddenException("Only the owner can change a franchise");
    }
    if (!RoomStateMachine.preAuction(access.room().state())) {
      throw new RoomConflictException(
          RoomConflictException.ROOM_LOCKED, "Franchises are locked once the auction is live");
    }
    if (request == null
        || (request.name() == null && request.colour() == null && request.monogram() == null)) {
      throw new RoomsRequestException("franchise", "Nothing to change");
    }
    var clean =
        FranchiseRules.clean(
            request.name() == null ? franchise.name() : request.name(),
            request.colour() == null ? franchise.colour() : request.colour(),
            request.monogram() == null ? franchise.monogram() : request.monogram());
    assertFree(roomId, clean.name(), clean.colour(), franchise.id());
    try {
      franchises.update(
          franchise.id(), clean.name(), clean.colour(), clean.monogram(), clock.instant());
    } catch (DuplicateKeyException e) {
      throw new RoomConflictException(
          RoomConflictException.DUPLICATE, "That name or colour is already taken");
    }
    return toDto(franchises.findById(franchise.id()).orElseThrow());
  }

  /**
   * Adds a franchise for {@code owner}: the room must be before the auction and not full, and the
   * name and colour must be free in this room.
   */
  Franchise create(Room room, UUID owner, FranchiseRequest request, LogKind logKind) {
    if (!RoomStateMachine.preAuction(room.state())) {
      throw new RoomConflictException(
          RoomConflictException.ROOM_LOCKED, "The room no longer takes new franchises");
    }
    if (franchises.count(room.id()) >= room.config().maxManagers()) {
      throw new RoomConflictException(RoomConflictException.ROOM_FULL, "The room is full");
    }
    if (request == null) {
      throw new RoomsRequestException("franchise", "Franchise details are required");
    }
    var clean = FranchiseRules.clean(request.name(), request.colour(), request.monogram());
    assertFree(room.id(), clean.name(), clean.colour(), null);
    Instant now = clock.instant();
    var franchise =
        new Franchise(
            UUID.randomUUID(),
            room.id(),
            owner,
            clean.name(),
            clean.colour(),
            clean.monogram(),
            now,
            now);
    try {
      franchises.insert(franchise);
    } catch (DuplicateKeyException e) {
      throw new RoomConflictException(
          RoomConflictException.DUPLICATE, "That name, colour or user is already in the room");
    }
    log.append(
        room.id(),
        logKind,
        owner,
        Map.of("franchiseId", franchise.id().toString(), "name", franchise.name()),
        now);
    return franchise;
  }

  /** 409 when another franchise of the room has the name (ignoring case) or the colour. */
  void assertFree(UUID roomId, String name, String colour, UUID except) {
    List<Franchise> others = franchises.findByRoom(roomId);
    for (Franchise other : others) {
      if (other.id().equals(except)) {
        continue;
      }
      if (FranchiseRules.nameKey(other.name()).equals(FranchiseRules.nameKey(name))) {
        throw new RoomConflictException(
            RoomConflictException.DUPLICATE, "Another franchise already has that name");
      }
      if (other.colour().equals(colour)) {
        throw new RoomConflictException(
            RoomConflictException.DUPLICATE, "Another franchise already has that colour");
      }
    }
  }

  static FranchiseDto toDto(Franchise f) {
    return new FranchiseDto(f.id(), f.ownerUserId(), f.name(), f.colour(), f.monogram());
  }
}
