package com.paddleup.rooms.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.rooms.dto.JoinPreviewDto;
import com.paddleup.rooms.dto.JoinRequest;
import com.paddleup.rooms.dto.JoinResultDto;
import com.paddleup.rooms.exception.InvalidInviteException;
import com.paddleup.rooms.exception.RoomsRequestException;
import com.paddleup.rooms.model.Franchise;
import com.paddleup.rooms.model.Invite;
import com.paddleup.rooms.model.LogKind;
import com.paddleup.rooms.model.Room;
import com.paddleup.rooms.repository.FranchiseRepository;
import com.paddleup.rooms.repository.RoomRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Redeeming invite codes. Failed attempts are rate-limited before the code is looked up, and a
 * failure is committed (so it counts) before the error goes back to the caller.
 */
@Service
public class JoinService {

  /** The code was no good; carried out of the transaction so the failure is committed first. */
  private static final Object INVALID = new Object();

  private final InviteService invites;
  private final RoomRepository rooms;
  private final FranchiseRepository franchises;
  private final FranchiseService franchiseService;
  private final JoinThrottle throttle;
  private final TransactionTemplate tx;
  private final Clock clock;

  JoinService(
      InviteService invites,
      RoomRepository rooms,
      FranchiseRepository franchises,
      FranchiseService franchiseService,
      JoinThrottle throttle,
      TransactionTemplate tx,
      Clock clock) {
    this.invites = invites;
    this.rooms = rooms;
    this.franchises = franchises;
    this.franchiseService = franchiseService;
    this.throttle = throttle;
    this.tx = tx;
    this.clock = clock;
  }

  /**
   * Joins with a code and creates the caller's franchise, or returns the one they already have.
   * {@code clientKey} is the rate-limit key of the caller's address.
   */
  public JoinResultDto redeem(UserId caller, JoinRequest request, String clientKey) {
    if (request == null) {
      throw new RoomsRequestException("code", "A code is required");
    }
    return attempt(
        caller,
        request.code(),
        clientKey,
        (invite, room) -> {
          Optional<Franchise> existing = franchises.findByOwner(room.id(), caller.value());
          Franchise franchise =
              existing.orElseGet(
                  () ->
                      franchiseService.create(
                          room, caller.value(), request.franchise(), LogKind.MANAGER_JOINED));
          return new JoinResultDto(room.id(), FranchiseService.toDto(franchise));
        });
  }

  /** What a valid code leads to, counted against the same limits as a redeem. */
  public JoinPreviewDto preview(UserId caller, String code, String clientKey) {
    return attempt(
        caller,
        code,
        clientKey,
        (invite, room) -> {
          var all = franchises.findByRoom(room.id());
          var mine = all.stream().filter(f -> f.ownerUserId().equals(caller.value())).findFirst();
          return new JoinPreviewDto(
              room.config().name(),
              all.stream().map(Franchise::colour).toList(),
              all.size() >= room.config().maxManagers(),
              mine.isPresent(),
              mine.map(f -> room.id()).orElse(null));
        });
  }

  private interface OnValid<T> {
    T apply(Invite invite, Room room);
  }

  private <T> T attempt(UserId caller, String code, String clientKey, OnValid<T> onValid) {
    Supplier<Object> work =
        () -> {
          throttle.admit(caller.value(), clientKey);
          Optional<Invite> invite = invites.find(code).filter(i -> i.activeAt(clock.instant()));
          Optional<Room> room = invite.flatMap(i -> rooms.findForUpdate(i.roomId()));
          // Revoking takes the room lock, so with it held the invite is read again as committed.
          Optional<Invite> still =
              invite.flatMap(i -> invites.find(i.code())).filter(i -> i.activeAt(clock.instant()));
          if (room.isEmpty() || still.isEmpty()) {
            throttle.recordFailure(caller.value(), clientKey);
            return INVALID;
          }
          return onValid.apply(still.get(), room.get());
        };
    Object result = tx.execute(status -> work.get());
    if (result == INVALID) {
      throw new InvalidInviteException();
    }
    @SuppressWarnings("unchecked")
    T value = (T) result;
    return value;
  }
}
