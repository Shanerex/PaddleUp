package com.paddleup.rooms.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.accounts.service.DisplayNameSanitiser;
import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.exception.PackNotFoundException;
import com.paddleup.packs.service.PackService;
import com.paddleup.rooms.dto.CreateRoomRequest;
import com.paddleup.rooms.dto.PackChoiceDto;
import com.paddleup.rooms.dto.RoomConfigDto;
import com.paddleup.rooms.dto.RoomDto;
import com.paddleup.rooms.dto.RoomState;
import com.paddleup.rooms.dto.RoomSummaryDto;
import com.paddleup.rooms.dto.SaveConfigRequest;
import com.paddleup.rooms.dto.Violation;
import com.paddleup.rooms.exception.InvalidConfigException;
import com.paddleup.rooms.exception.RoomConflictException;
import com.paddleup.rooms.exception.RoomsRequestException;
import com.paddleup.rooms.model.LogKind;
import com.paddleup.rooms.model.Room;
import com.paddleup.rooms.repository.FranchiseRepository;
import com.paddleup.rooms.repository.RoomLogRepository;
import com.paddleup.rooms.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating rooms, reading them and saving their config. */
@Service
public class RoomService {

  private final PackService packs;
  private final RoomConfigFactory factory;
  private final RoomConfigValidator validator;
  private final RoomRepository rooms;
  private final FranchiseRepository franchises;
  private final RoomLogRepository log;
  private final MembershipGuard guard;
  private final RoomViews views;
  private final PoolService pool;
  private final Clock clock;

  RoomService(
      PackService packs,
      RoomConfigFactory factory,
      RoomConfigValidator validator,
      RoomRepository rooms,
      FranchiseRepository franchises,
      RoomLogRepository log,
      MembershipGuard guard,
      RoomViews views,
      PoolService pool,
      Clock clock) {
    this.packs = packs;
    this.factory = factory;
    this.validator = validator;
    this.rooms = rooms;
    this.franchises = franchises;
    this.log = log;
    this.guard = guard;
    this.views = views;
    this.pool = pool;
    this.clock = clock;
  }

  /** Rooms on the caller's home hub; only rooms they are a member of (R-12). */
  @Transactional(readOnly = true)
  public List<RoomSummaryDto> listMine(UserId caller) {
    UUID me = caller.value();
    return rooms.findForMember(me, MY_ROOMS_LIMIT).stream()
        .map(
            room -> {
              var all = franchises.findByRoom(room.id());
              var mine =
                  all.stream()
                      .filter(f -> f.ownerUserId().equals(me))
                      .findFirst()
                      .map(FranchiseService::toDto)
                      .orElse(null);
              return new RoomSummaryDto(
                  room.id(),
                  room.config().name(),
                  room.config().competition() == null ? "" : room.config().competition(),
                  room.state(),
                  room.packId(),
                  room.packSnapshot().packName(),
                  room.commissionerUserId().equals(me),
                  all.size(),
                  room.config().minManagers(),
                  room.config().maxManagers(),
                  mine,
                  room.updatedAt());
            })
        .toList();
  }

  private static final int MY_ROOMS_LIMIT = 100;

  /** The packs a room can start from, each with its presets. */
  public List<PackChoiceDto> packChoices() {
    return packs.list().stream()
        .map(summary -> packs.get(summary.id()))
        .map(
            pack ->
                new PackChoiceDto(
                    pack.id(),
                    pack.version(),
                    pack.presets().stream()
                        .map(p -> new PackChoiceDto.Preset(p.id(), p.label()))
                        .toList(),
                    pack.display().name(),
                    pack.display().summary(),
                    pack.display().icon()))
        .toList();
  }

  /** Creates a room in {@code setup} with the caller as Commissioner and the preset as config. */
  @Transactional
  public RoomDto create(UserId caller, CreateRoomRequest request) {
    if (request == null || request.packId() == null) {
      throw new RoomsRequestException("packId", "Choose a pack");
    }
    PackDto pack = pack(request.packId());
    PresetDto preset =
        pack.presets().stream()
            .filter(p -> p.id().equals(request.presetId()))
            .findFirst()
            .orElseThrow(() -> new RoomsRequestException("presetId", "Unknown preset"));
    RoomConfigDto config =
        factory.create(
            pack,
            preset,
            DisplayNameSanitiser.sanitise(request.name()),
            DisplayNameSanitiser.sanitise(request.competition()));
    var snapshot = factory.snapshot(pack);
    failOn(validator.validate(config, snapshot, 0));
    Instant now = clock.instant();
    Room room =
        new Room(
            UUID.randomUUID(),
            RoomState.SETUP,
            caller.value(),
            pack.id(),
            pack.version(),
            preset.id(),
            snapshot,
            config,
            1,
            now,
            now);
    rooms.insert(room);
    log.append(
        room.id(),
        LogKind.ROOM_CREATED,
        caller.value(),
        Map.of("packId", pack.id(), "packVersion", pack.version(), "presetId", preset.id()),
        now);
    pool.copyLatest(room, caller);
    return views.room(guard.member(room.id(), caller));
  }

  @Transactional(readOnly = true)
  public RoomDto get(UserId caller, UUID roomId) {
    return views.room(guard.member(roomId, caller));
  }

  /**
   * Saves a whole config for the Commissioner while the room is before the auction. A stale version
   * changes nothing.
   */
  @Transactional
  public RoomDto saveConfig(UserId caller, UUID roomId, SaveConfigRequest request) {
    var access = guard.commissioner(roomId, caller);
    Room room = access.room();
    if (!RoomStateMachine.preAuction(room.state())) {
      throw new RoomConflictException(
          RoomConflictException.ROOM_LOCKED, "The config is locked once the auction is live");
    }
    if (request == null || request.config() == null) {
      throw new RoomsRequestException("config", "Config is required");
    }
    if (request.version() != room.configVersion()) {
      throw new RoomConflictException(
          RoomConflictException.STALE_VERSION,
          "The config changed since you opened it; reload and try again");
    }
    RoomConfigDto config =
        request
            .config()
            .withName(
                DisplayNameSanitiser.sanitise(request.config().name()),
                DisplayNameSanitiser.sanitise(request.config().competition()));
    failOn(validator.validate(config, room.packSnapshot(), franchises.count(roomId)));
    pool.requireFits(roomId, config);
    Instant now = clock.instant();
    if (!rooms.updateConfig(roomId, config, room.configVersion(), now)) {
      throw new RoomConflictException(
          RoomConflictException.STALE_VERSION, "The config changed since you opened it");
    }
    log.append(
        roomId,
        LogKind.CONFIG_SAVED,
        caller.value(),
        Map.of("fromVersion", room.configVersion()),
        now);
    return views.room(guard.member(roomId, caller));
  }

  private PackDto pack(String packId) {
    try {
      return packs.get(packId);
    } catch (PackNotFoundException e) {
      throw new RoomsRequestException("packId", "Unknown pack");
    }
  }

  private static void failOn(List<Violation> violations) {
    if (!violations.isEmpty()) {
      throw new InvalidConfigException(violations);
    }
  }
}
