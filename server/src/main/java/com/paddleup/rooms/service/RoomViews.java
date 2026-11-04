package com.paddleup.rooms.service;

import com.paddleup.accounts.dto.ProfileDto;
import com.paddleup.accounts.dto.UserId;
import com.paddleup.accounts.service.ProfileService;
import com.paddleup.rooms.dto.MemberDto;
import com.paddleup.rooms.dto.RoomDto;
import com.paddleup.rooms.dto.UserRefDto;
import com.paddleup.rooms.model.Franchise;
import com.paddleup.rooms.model.Room;
import com.paddleup.rooms.repository.FranchiseRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds the wire views of rooms and people from stored rows. */
@Component
class RoomViews {

  private final FranchiseRepository franchises;
  private final ProfileService profiles;

  RoomViews(FranchiseRepository franchises, ProfileService profiles) {
    this.franchises = franchises;
    this.profiles = profiles;
  }

  RoomDto room(MembershipGuard.Access access) {
    Room room = access.room();
    return new RoomDto(
        room.id(),
        room.state(),
        access.role(),
        user(room.commissionerUserId()),
        room.configVersion(),
        room.config(),
        room.packSnapshot(),
        franchises.count(room.id()),
        access.franchise().map(Franchise::id).orElse(null),
        access.commissioner() ? RoomStateMachine.nextOverRest(room.state()) : List.of(),
        room.createdAt());
  }

  MemberDto member(UUID userId, String role, Franchise franchise) {
    ProfileDto profile = profiles.get(new UserId(userId));
    return new MemberDto(
        userId,
        profile.displayName(),
        profile.avatar(),
        role,
        franchise == null ? null : FranchiseService.toDto(franchise));
  }

  UserRefDto user(UUID userId) {
    ProfileDto profile = profiles.get(new UserId(userId));
    return new UserRefDto(profile.id(), profile.displayName());
  }
}
