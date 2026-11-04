package com.paddleup.rooms.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.rooms.config.RoomsProperties;
import com.paddleup.rooms.dto.InviteDto;
import com.paddleup.rooms.exception.NoInviteException;
import com.paddleup.rooms.exception.RoomConflictException;
import com.paddleup.rooms.exception.RoomForbiddenException;
import com.paddleup.rooms.model.Invite;
import com.paddleup.rooms.model.LogKind;
import com.paddleup.rooms.repository.InviteRepository;
import com.paddleup.rooms.repository.RoomLogRepository;
import com.paddleup.rooms.util.InviteCodes;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The room's one active invite. Expiry is judged by the server clock only. Every issue, regenerate
 * and revoke is logged.
 */
@Service
public class InviteService {

  private final InviteRepository invites;
  private final RoomLogRepository log;
  private final MembershipGuard guard;
  private final RoomsProperties props;
  private final Clock clock;

  InviteService(
      InviteRepository invites,
      RoomLogRepository log,
      MembershipGuard guard,
      RoomsProperties props,
      Clock clock) {
    this.invites = invites;
    this.log = log;
    this.guard = guard;
    this.props = props;
    this.clock = clock;
  }

  /** The active invite, or {@link NoInviteException}. Commissioner only. */
  @Transactional(readOnly = true)
  public InviteDto current(UserId caller, UUID roomId) {
    var access = guard.member(roomId, caller);
    if (!access.commissioner()) {
      throw new RoomForbiddenException("Only the Commissioner can do this");
    }
    return invites
        .findUnrevoked(roomId)
        .filter(i -> i.activeAt(clock.instant()))
        .map(this::toDto)
        .orElseThrow(NoInviteException::new);
  }

  /**
   * Issues a new code with a fresh 7 days and revokes the one before it, if any. Commissioner only,
   * and only before the auction is live.
   */
  @Transactional
  public InviteDto issue(UserId caller, UUID roomId) {
    var access = guard.commissioner(roomId, caller);
    if (!RoomStateMachine.preAuction(access.room().state())) {
      throw new RoomConflictException(
          RoomConflictException.ROOM_LOCKED, "Invites close once the auction is live");
    }
    Instant now = clock.instant();
    Optional<Invite> old = invites.findUnrevoked(roomId);
    old.ifPresent(i -> invites.revoke(i.id(), now));
    String code = newCode();
    Invite invite =
        new Invite(
            UUID.randomUUID(),
            roomId,
            code,
            caller.value(),
            now,
            now.plus(props.inviteTtl()),
            null);
    invites.insert(invite);
    log.append(
        roomId,
        old.isPresent() ? LogKind.INVITE_REGENERATED : LogKind.INVITE_ISSUED,
        caller.value(),
        Map.of("expiresAt", invite.expiresAt().toString()),
        now);
    return toDto(invite);
  }

  /** Revokes the active invite so its code stops working; members already in are unaffected. */
  @Transactional
  public void revoke(UserId caller, UUID roomId) {
    guard.commissioner(roomId, caller);
    Instant now = clock.instant();
    invites
        .findUnrevoked(roomId)
        .ifPresent(
            i -> {
              invites.revoke(i.id(), now);
              log.append(roomId, LogKind.INVITE_REVOKED, caller.value(), Map.of(), now);
            });
  }

  /** Revokes the active invite as part of a room change, such as the auction going live. */
  void revokeForRoom(UUID roomId, UUID actor, String reason) {
    Instant now = clock.instant();
    invites
        .findUnrevoked(roomId)
        .ifPresent(
            i -> {
              invites.revoke(i.id(), now);
              log.append(roomId, LogKind.INVITE_REVOKED, actor, Map.of("reason", reason), now);
            });
  }

  /** The invite for a typed code, whether or not it is still good; null for a malformed code. */
  Optional<Invite> find(String rawCode) {
    String code = InviteCodes.normalise(rawCode);
    return code == null || code.isEmpty() ? Optional.empty() : invites.findByCode(code);
  }

  private String newCode() {
    String code;
    do {
      code = InviteCodes.generate(props.codeLength());
    } while (invites.findByCode(code).isPresent());
    return code;
  }

  private InviteDto toDto(Invite invite) {
    return new InviteDto(
        invite.code(), props.webBaseUrl() + "/join/" + invite.code(), invite.expiresAt());
  }
}
