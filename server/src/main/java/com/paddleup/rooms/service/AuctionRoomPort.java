package com.paddleup.rooms.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.packs.dto.RestrictedCategoryDto;
import com.paddleup.rooms.dto.AuctionInputsDto;
import com.paddleup.rooms.dto.RoomAccessDto;
import com.paddleup.rooms.dto.RoomState;
import com.paddleup.rooms.exception.RoomNotFoundException;
import com.paddleup.rooms.model.Franchise;
import com.paddleup.rooms.model.PoolPlayer;
import com.paddleup.rooms.model.Room;
import com.paddleup.rooms.model.RoomPool;
import com.paddleup.rooms.repository.FranchiseRepository;
import com.paddleup.rooms.repository.RoomPoolRepository;
import com.paddleup.rooms.repository.RoomRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one door the auction module uses to read a room. Rooms keeps owning room state, the pool and
 * the config; the auction asks who may do what, whether a start is allowed, and what to freeze at
 * go-live. State changes go through {@link RoomLifecycle}.
 */
@Service
public class AuctionRoomPort {

  private final MembershipGuard guard;
  private final RoomRepository rooms;
  private final FranchiseRepository franchises;
  private final RoomPoolRepository pools;
  private final RoomLifecycle lifecycle;

  AuctionRoomPort(
      MembershipGuard guard,
      RoomRepository rooms,
      FranchiseRepository franchises,
      RoomPoolRepository pools,
      RoomLifecycle lifecycle) {
    this.guard = guard;
    this.rooms = rooms;
    this.franchises = franchises;
    this.pools = pools;
    this.lifecycle = lifecycle;
  }

  /**
   * The caller's standing in the room. Throws {@code RoomNotFoundException} (404) for a non-member,
   * so a room id reveals nothing.
   */
  @Transactional(readOnly = true)
  public RoomAccessDto access(UUID roomId, UserId user) {
    var access = guard.member(roomId, user);
    return new RoomAccessDto(
        roomId,
        access.room().state(),
        access.commissioner(),
        access.franchise().map(Franchise::id).orElse(null),
        franchises.count(roomId));
  }

  /**
   * Why the room cannot go live now, or empty when it can: too few franchises, no pool copy, or a
   * pool too small to fill every squad.
   */
  @Transactional(readOnly = true)
  public Optional<String> auctionRefusal(UUID roomId) {
    Room room = rooms.find(roomId).orElseThrow(RoomNotFoundException::new);
    int count = franchises.count(roomId);
    if (count < room.config().minManagers()) {
      return Optional.of(
          "Needs at least " + room.config().minManagers() + " franchises; has " + count);
    }
    return lifecycle.poolRefusal(room);
  }

  /**
   * Moves the room to {@code auction_scheduled} for its first schedule; a room already there (the
   * Commissioner moved it through REST) stays. The room row is locked until the caller's
   * transaction ends. Throws a 409 {@code RoomConflictException} when the move is not legal.
   */
  @Transactional
  public void markScheduled(UUID roomId, UserId actor) {
    Room room = rooms.findForUpdate(roomId).orElseThrow(RoomNotFoundException::new);
    if (room.state() != RoomState.AUCTION_SCHEDULED) {
      lifecycle.advance(roomId, RoomState.AUCTION_SCHEDULED, actor);
    }
  }

  /**
   * The room's rules, franchises, set order and players in play order. The room row stays locked
   * until the caller's transaction ends, so pool and config writes wait for go-live to finish.
   */
  @Transactional
  public AuctionInputsDto liveInputs(UUID roomId) {
    Room room = rooms.findForUpdate(roomId).orElseThrow(RoomNotFoundException::new);
    RoomPool pool = pools.find(roomId).orElseThrow(RoomNotFoundException::new);
    List<String> setOrder = pool.setOrder();
    List<PoolPlayer> players = new ArrayList<>(pools.players(roomId));
    // Stable sort: players already come ordered by position, then id, within a set.
    players.sort(Comparator.comparingInt(p -> setIndex(setOrder, p.set())));
    return new AuctionInputsDto(
        room.config(),
        franchises.findByRoom(roomId).stream()
            .map(f -> new AuctionInputsDto.Franchise(f.id(), f.name()))
            .toList(),
        List.copyOf(setOrder),
        players.stream()
            .map(
                p ->
                    new AuctionInputsDto.Player(
                        p.id(),
                        p.name(),
                        p.country(),
                        p.role(),
                        List.copyOf(p.tags()),
                        p.basePrice(),
                        p.set(),
                        p.position()))
            .toList(),
        quotas(room));
  }

  /**
   * The room's cap per restricted category, with the pack tag it counts (snapshotted at creation).
   */
  private static List<AuctionInputsDto.Quota> quotas(Room room) {
    List<AuctionInputsDto.Quota> quotas = new ArrayList<>();
    for (RestrictedCategoryDto category : room.packSnapshot().restrictedCategories()) {
      Integer max = room.config().categoryQuotas().get(category.id());
      if (max != null) {
        quotas.add(new AuctionInputsDto.Quota(category.id(), category.tag(), max));
      }
    }
    return quotas;
  }

  private static int setIndex(List<String> setOrder, String set) {
    int index = setOrder.indexOf(set);
    return index < 0 ? setOrder.size() : index;
  }
}
