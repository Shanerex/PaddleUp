package com.paddleup.rooms.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.accounts.service.DisplayNameSanitiser;
import com.paddleup.packs.dto.DatasetSummaryDto;
import com.paddleup.packs.dto.FieldError;
import com.paddleup.packs.dto.PlayerInput;
import com.paddleup.packs.dto.RoleDto;
import com.paddleup.packs.dto.SeasonDatasetDto;
import com.paddleup.packs.dto.SeasonPlayerDto;
import com.paddleup.packs.dto.TagDto;
import com.paddleup.packs.dto.ValidPlayer;
import com.paddleup.packs.service.PlayerRowRules;
import com.paddleup.packs.service.PriceRule;
import com.paddleup.packs.service.SeasonDatasetService;
import com.paddleup.rooms.dto.PackLabelsDto;
import com.paddleup.rooms.dto.PoolDto;
import com.paddleup.rooms.dto.PoolOrigin;
import com.paddleup.rooms.dto.PoolPlayerDto;
import com.paddleup.rooms.dto.PoolPlayerRequest;
import com.paddleup.rooms.dto.PoolResetRequest;
import com.paddleup.rooms.dto.PoolSetDto;
import com.paddleup.rooms.dto.PoolSetsRequest;
import com.paddleup.rooms.dto.Violation;
import com.paddleup.rooms.exception.PoolPlayerNotFoundException;
import com.paddleup.rooms.exception.PoolRejectedException;
import com.paddleup.rooms.exception.RoomConflictException;
import com.paddleup.rooms.exception.RoomsRequestException;
import com.paddleup.rooms.model.LogKind;
import com.paddleup.rooms.model.PoolPlayer;
import com.paddleup.rooms.model.Room;
import com.paddleup.rooms.model.RoomPool;
import com.paddleup.rooms.repository.RoomLogRepository;
import com.paddleup.rooms.repository.RoomPoolRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A room's player pool: a copy of the latest season dataset taken at room creation, which only the
 * Commissioner edits, and only before the auction. Every write locks the room row (the row the
 * lifecycle locks to go live), checks the pool version and records a log entry. Writes are
 * all-or-nothing.
 */
@Service
public class PoolService {

  /** Most players a Commissioner may add up to; a copy itself may be larger. */
  public static final int PLAYERS_MAX = 1000;

  private final RoomPoolRepository pools;
  private final SeasonDatasetService datasets;
  private final MembershipGuard guard;
  private final RoomLogRepository log;
  private final Clock clock;

  PoolService(
      RoomPoolRepository pools,
      SeasonDatasetService datasets,
      MembershipGuard guard,
      RoomLogRepository log,
      Clock clock) {
    this.pools = pools;
    this.datasets = datasets;
    this.guard = guard;
    this.log = log;
    this.clock = clock;
  }

  // ---- copy at creation -------------------------------------------------------------------

  /**
   * Copies the latest dataset for the room's pack into the room, in the caller's transaction. With
   * no dataset, or one the room's pack snapshot cannot describe, nothing is written; the
   * Commissioner can load season data later.
   */
  void copyLatest(Room room, UserId actor) {
    Optional<SeasonDatasetDto> latest = datasets.latest(room.packId());
    if (latest.isEmpty() || !compatible(room, latest.get())) {
      return;
    }
    Instant now = clock.instant();
    createCopy(room.id(), latest.get(), now);
    log.append(
        room.id(),
        LogKind.POOL_COPIED,
        actor.value(),
        Map.of("datasetVersion", latest.get().version(), "players", latest.get().playerCount()),
        now);
  }

  // ---- read -------------------------------------------------------------------------------

  /** The pool for any member. */
  @Transactional(readOnly = true)
  public PoolDto get(UserId caller, UUID roomId) {
    return view(guard.member(roomId, caller));
  }

  // ---- writes -----------------------------------------------------------------------------

  @Transactional
  public PoolDto add(UserId caller, UUID roomId, PoolPlayerRequest request) {
    var access = writable(caller, roomId);
    RoomPool header = existing(roomId);
    requireVersion(header, version(request));
    List<PoolPlayer> players = pools.players(roomId);
    if (players.size() >= PLAYERS_MAX) {
      throw new PoolRejectedException(
          List.of(new Violation("pool", "A pool holds at most " + PLAYERS_MAX + " players.")));
    }
    ValidPlayer valid = checked(access.room(), header, request);
    Instant now = clock.instant();
    var row =
        new PoolPlayer(
            UUID.randomUUID(),
            null,
            PoolOrigin.ADDED,
            valid.name(),
            valid.country(),
            valid.role(),
            valid.tags(),
            valid.basePrice(),
            valid.set(),
            valid.providerId(),
            pools.nextPosition(roomId));
    pools.insertPlayers(roomId, List.of(row));
    bump(roomId, header, null, now);
    log.append(
        roomId,
        LogKind.POOL_PLAYER_ADDED,
        caller.value(),
        Map.of(
            "playerId",
            row.id().toString(),
            "name",
            row.name(),
            "fromVersion",
            header.poolVersion()),
        now);
    return view(guard.member(roomId, caller));
  }

  @Transactional
  public PoolDto edit(UserId caller, UUID roomId, UUID playerId, PoolPlayerRequest request) {
    var access = writable(caller, roomId);
    RoomPool header = existing(roomId);
    requireVersion(header, version(request));
    PoolPlayer current =
        pools.player(roomId, playerId).orElseThrow(PoolPlayerNotFoundException::new);
    ValidPlayer valid = checked(access.room(), header, request);
    Instant now = clock.instant();
    var updated =
        new PoolPlayer(
            current.id(),
            current.sourcePlayerId(),
            originAfterEdit(header, current, valid),
            valid.name(),
            valid.country(),
            valid.role(),
            valid.tags(),
            valid.basePrice(),
            valid.set(),
            valid.providerId(),
            current.position());
    pools.updatePlayer(roomId, updated);
    bump(roomId, header, null, now);
    log.append(
        roomId,
        LogKind.POOL_PLAYER_EDITED,
        caller.value(),
        Map.of(
            "playerId",
            playerId.toString(),
            "name",
            updated.name(),
            "fromVersion",
            header.poolVersion()),
        now);
    return view(guard.member(roomId, caller));
  }

  @Transactional
  public PoolDto remove(UserId caller, UUID roomId, UUID playerId, int version) {
    writable(caller, roomId);
    RoomPool header = existing(roomId);
    requireVersion(header, version);
    PoolPlayer current =
        pools.player(roomId, playerId).orElseThrow(PoolPlayerNotFoundException::new);
    Instant now = clock.instant();
    pools.deletePlayer(roomId, playerId);
    bump(roomId, header, null, now);
    log.append(
        roomId,
        LogKind.POOL_PLAYER_REMOVED,
        caller.value(),
        Map.of(
            "playerId",
            playerId.toString(),
            "name",
            current.name(),
            "fromVersion",
            header.poolVersion()),
        now);
    return view(guard.member(roomId, caller));
  }

  /**
   * Replaces the players and set order with the latest dataset, discarding every edit. With no copy
   * yet the version must be 0 and the copy is created.
   */
  @Transactional
  public PoolDto reset(UserId caller, UUID roomId, PoolResetRequest request) {
    var access = writable(caller, roomId);
    if (request == null) {
      throw new RoomsRequestException("version", "Version is required");
    }
    Room room = access.room();
    Optional<RoomPool> header = pools.find(roomId);
    int expected = header.map(RoomPool::poolVersion).orElse(0);
    if (request.version() != expected) {
      throw stale();
    }
    SeasonDatasetDto latest =
        datasets
            .latest(room.packId())
            .orElseThrow(
                () ->
                    new RoomConflictException(
                        RoomConflictException.ILLEGAL_STATE, "No season data has been loaded yet"));
    if (!compatible(room, latest)) {
      throw new RoomConflictException(
          RoomConflictException.PACK_MISMATCH,
          "The season data was made for a different version of this pack");
    }
    Instant now = clock.instant();
    if (header.isEmpty()) {
      createCopy(roomId, latest, now);
    } else {
      pools.deletePlayers(roomId);
      pools.replaceSource(
          roomId,
          latest.id(),
          latest.version(),
          latest.playerCount(),
          latest.setOrder(),
          expected,
          now);
      pools.insertPlayers(roomId, copies(latest));
    }
    log.append(
        roomId,
        LogKind.POOL_RESET,
        caller.value(),
        Map.of("datasetVersion", latest.version(), "fromVersion", expected),
        now);
    return view(guard.member(roomId, caller));
  }

  /** Saves the whole set order; it must list every current set exactly once. */
  @Transactional
  public PoolDto saveSetOrder(UserId caller, UUID roomId, PoolSetsRequest request) {
    writable(caller, roomId);
    RoomPool header = existing(roomId);
    if (request == null || request.order() == null) {
      throw new RoomsRequestException("order", "Order is required");
    }
    requireVersion(header, request.version());
    List<String> order = request.order();
    boolean permutation =
        order.size() == header.setOrder().size()
            && new HashSet<>(order).size() == order.size()
            && new HashSet<>(order).equals(new HashSet<>(header.setOrder()));
    if (!permutation) {
      throw new PoolRejectedException(
          List.of(new Violation("order", "The order must list every set exactly once.")));
    }
    Instant now = clock.instant();
    bump(roomId, header, List.copyOf(order), now);
    log.append(
        roomId,
        LogKind.SET_ORDER_SAVED,
        caller.value(),
        Map.of("fromVersion", header.poolVersion(), "sets", order.size()),
        now);
    return view(guard.member(roomId, caller));
  }

  // ---- helpers ----------------------------------------------------------------------------

  /** The Commissioner, with the room locked, before the auction; 403 or 409 otherwise. */
  private MembershipGuard.Access writable(UserId caller, UUID roomId) {
    var access = guard.commissioner(roomId, caller);
    if (!RoomStateMachine.preAuction(access.room().state())) {
      throw new RoomConflictException(
          RoomConflictException.ROOM_LOCKED, "The player pool is locked once the auction is live");
    }
    return access;
  }

  private RoomPool existing(UUID roomId) {
    return pools
        .find(roomId)
        .orElseThrow(
            () ->
                new RoomConflictException(
                    RoomConflictException.ILLEGAL_STATE, "This room has no player pool yet"));
  }

  private static int version(PoolPlayerRequest request) {
    if (request == null) {
      throw new RoomsRequestException("player", "Player is required");
    }
    return request.version();
  }

  private static void requireVersion(RoomPool header, int version) {
    if (header.poolVersion() != version) {
      throw stale();
    }
  }

  private static RoomConflictException stale() {
    return new RoomConflictException(
        RoomConflictException.STALE_VERSION,
        "The pool changed since you opened it; reload and try again");
  }

  private void bump(UUID roomId, RoomPool header, List<String> order, Instant now) {
    if (!pools.bump(roomId, order, header.poolVersion(), now)) {
      throw stale();
    }
  }

  /** Applies the row rules and the set rule; throws a 422 listing every broken field. */
  private ValidPlayer checked(Room room, RoomPool header, PoolPlayerRequest r) {
    PackLabelsDto snapshot = room.packSnapshot();
    var rules =
        new PlayerRowRules(
            snapshot.roles().stream().map(RoleDto::id).collect(Collectors.toSet()),
            snapshot.tags().stream().map(TagDto::id).collect(Collectors.toSet()),
            PriceRule.of(room.config().purse(), room.config().incrementLadder()),
            snapshot.packName() == null || snapshot.packName().isBlank()
                ? snapshot.packId()
                : snapshot.packName());
    var result =
        rules.check(
            new PlayerInput(
                r.name(),
                r.country(),
                r.role(),
                r.tags() == null ? List.of() : r.tags(),
                r.basePrice() == null ? null : Long.toString(r.basePrice()),
                r.set(),
                r.providerId()));
    var errors = new ArrayList<FieldError>(result.errors());
    boolean setBroken = errors.stream().anyMatch(e -> e.field().equals("set"));
    if (!setBroken && !header.setOrder().contains(DisplayNameSanitiser.sanitise(r.set()))) {
      errors.add(
          new FieldError("set", String.valueOf(r.set()), "Pick one of the sets in this pool."));
    }
    if (!errors.isEmpty()) {
      throw new PoolRejectedException(
          errors.stream().map(e -> new Violation(e.field(), e.message())).toList());
    }
    return result.player();
  }

  private PoolOrigin originAfterEdit(RoomPool header, PoolPlayer current, ValidPlayer next) {
    if (current.sourcePlayerId() == null) {
      return PoolOrigin.ADDED;
    }
    Optional<SeasonPlayerDto> source =
        datasets.player(header.datasetId(), current.sourcePlayerId());
    boolean same =
        source
            .filter(
                s ->
                    s.name().equals(next.name())
                        && s.country().equals(next.country())
                        && s.role().equals(next.role())
                        && s.tags().equals(next.tags())
                        && s.basePrice() == next.basePrice()
                        && s.set().equals(next.set())
                        && Objects.equals(s.providerId(), next.providerId()))
            .isPresent();
    return same ? PoolOrigin.SOURCE : PoolOrigin.EDITED;
  }

  /** Every role and tag of the dataset exists in the room's pack snapshot. */
  private static boolean compatible(Room room, SeasonDatasetDto dataset) {
    Set<String> roles =
        room.packSnapshot().roles().stream().map(RoleDto::id).collect(Collectors.toSet());
    Set<String> tags =
        room.packSnapshot().tags().stream().map(TagDto::id).collect(Collectors.toSet());
    return dataset.players().stream()
        .allMatch(p -> roles.contains(p.role()) && tags.containsAll(p.tags()));
  }

  private void createCopy(UUID roomId, SeasonDatasetDto dataset, Instant now) {
    pools.insertHeader(
        new RoomPool(
            roomId,
            dataset.id(),
            dataset.version(),
            dataset.playerCount(),
            1,
            dataset.setOrder(),
            now,
            now));
    pools.insertPlayers(roomId, copies(dataset));
  }

  private static List<PoolPlayer> copies(SeasonDatasetDto dataset) {
    return dataset.players().stream()
        .map(
            p ->
                new PoolPlayer(
                    UUID.randomUUID(),
                    p.id(),
                    PoolOrigin.SOURCE,
                    p.name(),
                    p.country(),
                    p.role(),
                    p.tags(),
                    p.basePrice(),
                    p.set(),
                    p.providerId(),
                    p.position()))
        .toList();
  }

  private PoolDto view(MembershipGuard.Access access) {
    Room room = access.room();
    Integer latest =
        datasets.latestSummary(room.packId()).map(DatasetSummaryDto::version).orElse(null);
    boolean canEdit = access.commissioner() && RoomStateMachine.preAuction(room.state());
    Optional<RoomPool> header = pools.find(room.id());
    if (header.isEmpty()) {
      return new PoolDto(false, 0, null, latest, 0, canEdit, List.of(), List.of());
    }
    List<PoolPlayer> players = pools.players(room.id());
    var counts = new LinkedHashMap<String, Integer>();
    header.get().setOrder().forEach(s -> counts.put(s, 0));
    players.forEach(p -> counts.merge(p.set(), 1, Integer::sum));
    long remainingSource = players.stream().filter(p -> p.sourcePlayerId() != null).count();
    long changed = players.stream().filter(p -> p.origin() != PoolOrigin.SOURCE).count();
    int editCount = (int) (changed + (header.get().sourceCount() - remainingSource));
    return new PoolDto(
        true,
        header.get().poolVersion(),
        header.get().datasetVersion(),
        latest,
        editCount,
        canEdit,
        players.stream()
            .map(
                p ->
                    new PoolPlayerDto(
                        p.id(),
                        p.name(),
                        p.country(),
                        p.role(),
                        p.tags(),
                        p.basePrice(),
                        p.set(),
                        p.providerId(),
                        p.origin()))
            .toList(),
        counts.entrySet().stream().map(e -> new PoolSetDto(e.getKey(), e.getValue())).toList());
  }
}
