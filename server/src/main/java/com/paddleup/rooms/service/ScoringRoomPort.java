package com.paddleup.rooms.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.packs.dto.AppearanceRuleDto;
import com.paddleup.packs.exception.PackNotFoundException;
import com.paddleup.packs.service.PackService;
import com.paddleup.rooms.dto.RoomAccessDto;
import com.paddleup.rooms.dto.ScoringInputsDto;
import com.paddleup.rooms.dto.ScoringPlayerDto;
import com.paddleup.rooms.exception.RoomForbiddenException;
import com.paddleup.rooms.exception.RoomNotFoundException;
import com.paddleup.rooms.model.Franchise;
import com.paddleup.rooms.model.Room;
import com.paddleup.rooms.repository.FranchiseRepository;
import com.paddleup.rooms.repository.RoomPoolRepository;
import com.paddleup.rooms.repository.RoomRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one door the scoring module uses to read a room. Rooms keeps owning the room, its pool and
 * its pack snapshot; scoring asks who may do what, which rules and vocabulary the room was created
 * with, which players are in its pool and which rooms share a season dataset. No method takes the
 * room row lock: scoring serialises its own writes on its own row.
 */
@Service
public class ScoringRoomPort {

  private static final Logger log = LoggerFactory.getLogger(ScoringRoomPort.class);

  private final MembershipGuard guard;
  private final RoomRepository rooms;
  private final FranchiseRepository franchises;
  private final RoomPoolRepository pools;
  private final PackService packs;
  private final Set<UUID> warnedLegacy = ConcurrentHashMap.newKeySet();

  ScoringRoomPort(
      MembershipGuard guard,
      RoomRepository rooms,
      FranchiseRepository franchises,
      RoomPoolRepository pools,
      PackService packs) {
    this.guard = guard;
    this.rooms = rooms;
    this.franchises = franchises;
    this.pools = pools;
    this.packs = packs;
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
   * As {@link #access}, and additionally throws {@code RoomForbiddenException} (403) when the
   * caller is a member but not the Commissioner.
   */
  @Transactional(readOnly = true)
  public RoomAccessDto requireCommissioner(UUID roomId, UserId user) {
    RoomAccessDto access = access(roomId, user);
    if (!access.commissioner()) {
      throw new RoomForbiddenException("Only the Commissioner can do this");
    }
    return access;
  }

  /**
   * The room's pack vocabulary and default rules from its creation snapshot, plus the season
   * dataset of its pool (null when it has no pool yet). A room made before the appearance rule was
   * snapshotted falls back to the live pack's rule, with one warning per room.
   */
  @Transactional(readOnly = true)
  public ScoringInputsDto ruleInputs(UUID roomId) {
    Room room = rooms.find(roomId).orElseThrow(RoomNotFoundException::new);
    var snapshot = room.packSnapshot();
    UUID datasetId = pools.find(roomId).map(p -> p.datasetId()).orElse(null);
    return new ScoringInputsDto(
        snapshot.packId(),
        room.packVersion(),
        snapshot.roles(),
        snapshot.stats(),
        room.config().scoringRules(),
        appearanceRule(room),
        datasetId);
  }

  /** The room's pool players with the dataset player each was copied from. */
  @Transactional(readOnly = true)
  public List<ScoringPlayerDto> players(UUID roomId) {
    return pools.players(roomId).stream()
        .map(p -> new ScoringPlayerDto(p.id(), p.sourcePlayerId(), p.role()))
        .toList();
  }

  /** Ids of the rooms whose pool was copied from this dataset. */
  @Transactional(readOnly = true)
  public List<UUID> roomsOnDataset(UUID datasetId) {
    return pools.roomsOnDataset(datasetId);
  }

  private AppearanceRuleDto appearanceRule(Room room) {
    AppearanceRuleDto stored = room.packSnapshot().appearanceRule();
    if (stored != null) {
      return stored;
    }
    if (warnedLegacy.add(room.id())) {
      log.warn(
          "Room {} has no appearance rule in its pack snapshot; using the live pack's rule",
          room.id());
    }
    try {
      return packs.get(room.packId()).appearanceRule();
    } catch (PackNotFoundException e) {
      return null;
    }
  }
}
