package com.paddleup.auction.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.auction.config.AuctionProperties;
import com.paddleup.auction.exception.AuctionConflictException;
import com.paddleup.auction.exception.AuctionRequestException;
import com.paddleup.auction.exception.LogWriteException;
import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionHeader;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Decision;
import com.paddleup.auction.model.EventKind;
import com.paddleup.auction.model.Events;
import com.paddleup.auction.model.Intent;
import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.Outcome;
import com.paddleup.auction.model.Phase;
import com.paddleup.auction.model.Reason;
import com.paddleup.auction.model.Step;
import com.paddleup.auction.repository.AuctionEventRepository;
import com.paddleup.auction.repository.AuctionLotRepository;
import com.paddleup.auction.repository.AuctionRejectionRepository;
import com.paddleup.auction.repository.AuctionRepository;
import com.paddleup.auction.util.SerialExecutor;
import com.paddleup.rooms.dto.AuctionInputsDto;
import com.paddleup.rooms.dto.RoomState;
import com.paddleup.rooms.exception.RoomConflictException;
import com.paddleup.rooms.service.AuctionRoomPort;
import com.paddleup.rooms.service.RoomLifecycle;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The single writer of one auction. Every intent, timer step and schedule change is a task on one
 * serial queue, so they apply one at a time in arrival order and no lock is needed. For each change
 * the runner decides it with the pure rules, persists the events in one transaction, applies them
 * to its in-memory state, broadcasts them and only then answers. If the log write fails nothing
 * else happens: the state is exactly as it was.
 */
final class AuctionRunner {

  private static final Logger log = LoggerFactory.getLogger(AuctionRunner.class);

  /** Most timer steps one task takes, so a clock far ahead cannot spin forever. */
  private static final int MAX_STEPS = 16;

  /** What a runner needs from the rest of the application. */
  record Deps(
      Clock clock,
      AuctionRepository headers,
      AuctionEventRepository events,
      AuctionLotRepository lots,
      AuctionRejectionRepository rejections,
      TransactionTemplate tx,
      AuctionBroadcaster broadcaster,
      Readiness readiness,
      List<BidGuard> guards,
      AuctionRoomPort port,
      RoomLifecycle lifecycle,
      AuctionProperties props,
      Executor pool) {}

  /** Room changes that must stand or fall with the events in one transaction. */
  private interface RoomWork {
    void apply();
  }

  private final UUID roomId;
  private final Deps d;
  private final SerialExecutor serial;

  private CompletableFuture<Void> queuedTick;
  private volatile AuctionState state;
  private UUID scheduledBy;
  private Instant startRefusedAt;
  private boolean pendingResume;

  private AuctionRunner(UUID roomId, Deps d, AuctionState state, AuctionHeader header) {
    this.roomId = roomId;
    this.d = d;
    this.serial = new SerialExecutor(d.pool());
    this.state = state;
    this.scheduledBy = header == null ? null : header.scheduledBy();
    this.startRefusedAt = header == null ? null : header.startRefusedAt();
    this.pendingResume = header != null;
  }

  /** Rebuilds the auction of {@code roomId} from its log; an empty one if it has none. */
  static AuctionRunner load(UUID roomId, Deps d) {
    Map<UUID, Lot> catalog = new LinkedHashMap<>();
    d.lots().byRoom(roomId).forEach(l -> catalog.put(l.playerId(), l));
    AuctionState state =
        AuctionReducer.replay(AuctionState.initial(roomId, catalog), d.events().all(roomId));
    return new AuctionRunner(roomId, d, state, d.headers().find(roomId).orElse(null));
  }

  /** The state as of the last committed event. Safe to read from any thread. */
  AuctionState state() {
    return state;
  }

  CompletableFuture<Outcome> intent(Intent intent) {
    return serial.submit(() -> handle(intent));
  }

  /**
   * Marks a franchise ready or not ready, on the serial queue so it cannot interleave with the
   * start of the auction that clears readiness. {@code onChange} is called there too, only when the
   * value changed. Refused unless the auction is scheduled and the caller has a franchise.
   */
  CompletableFuture<Boolean> ready(UUID franchiseId, boolean on, Consumer<UUID> onChange) {
    return serial.submit(
        () -> {
          if (state.phase() != Phase.SCHEDULED) {
            throw new AuctionConflictException(
                Reason.NOT_SCHEDULED, "Ready can only be set before the auction starts");
          }
          if (franchiseId == null) {
            throw new AuctionConflictException(
                Reason.NOT_A_FRANCHISE, "Only a franchise can mark itself ready");
          }
          boolean changed = d.readiness().set(roomId, franchiseId, on);
          if (changed) {
            onChange.accept(roomId);
          }
          return changed;
        });
  }

  CompletableFuture<AuctionState> schedule(UserId actor, Instant startsAt) {
    return serial.submit(() -> doSchedule(actor, startsAt));
  }

  /**
   * One clock step: go live at the start instant, close and open lots as they fall due. A tick
   * asked for while another is still waiting its turn shares it, so a slow auction never builds a
   * queue of clock steps; one that is already running does not count, since it may have read the
   * clock before this call.
   */
  synchronized CompletableFuture<Void> tick() {
    if (queuedTick == null) {
      queuedTick =
          serial.submit(
              () -> {
                startTick();
                doTick();
                return null;
              });
    }
    return queuedTick;
  }

  private synchronized void startTick() {
    queuedTick = null;
  }

  /**
   * The server clock, cut to microseconds: that is all a timestamptz keeps, so a time used by the
   * live state is exactly the time a replay of the stored log reads back.
   */
  private Instant now() {
    return d.clock().instant().truncatedTo(ChronoUnit.MICROS);
  }

  // ---- everything below happens on the serial queue ----

  private Outcome handle(Intent intent) {
    Instant now = now();
    resumeIfPending(now);
    Outcome prior = prior(intent);
    if (prior != null) {
      return prior;
    }
    advanceTimers(now);
    Decision decision = IntentRules.decide(state, intent, now, d.guards());
    return switch (decision) {
      case Decision.Accepted accepted -> {
        commit(accepted.events(), scheduledBy, null);
        yield outcome(accepted.events().get(0));
      }
      case Decision.Rejected rejected -> reject(intent, now, rejected.reason());
      case Decision.PhaseError phase ->
          throw new AuctionConflictException(phase.code(), phase.code());
    };
  }

  /**
   * Stores the rejection, then answers with it, so a resend of this intent id gets the same answer
   * even after a restart. If it cannot be stored nothing is answered and the client may retry.
   */
  private Outcome reject(Intent intent, Instant now, String reason) {
    Outcome out = Outcome.rejected(intent.lot(), reason);
    try {
      d.rejections().append(roomId, intent.actor(), intent.intentId(), out, now);
    } catch (DataAccessException e) {
      throw new LogWriteException(e);
    }
    return out;
  }

  /** The first answer to this intent id, if it was already decided. */
  private Outcome prior(Intent intent) {
    Optional<Outcome> rejected = d.rejections().find(roomId, intent.actor(), intent.intentId());
    if (rejected.isPresent()) {
      return rejected.get();
    }
    Optional<AuctionEvent> logged = d.events().byIntent(roomId, intent.actor(), intent.intentId());
    return logged.map(AuctionRunner::outcome).orElse(null);
  }

  private static Outcome outcome(AuctionEvent e) {
    boolean bid = e.kind() == EventKind.BID_ACCEPTED;
    return Outcome.accepted(
        e.lot(), e.seq(), bid ? e.number("amount") : null, bid ? e.instant("deadline") : null);
  }

  private AuctionState doSchedule(UserId actor, Instant startsAt) {
    Instant now = now();
    if (startsAt == null || !startsAt.isAfter(now)) {
      throw new AuctionRequestException("startsAt", "The start must be in the future");
    }
    Phase phase = state.phase();
    if (phase == Phase.LIVE || phase == Phase.ENDED) {
      throw new AuctionConflictException(
          AuctionConflictException.LOCKED,
          "The auction is already live, so it cannot be scheduled");
    }
    long seq = state.lastSeq() + 1;
    AuctionEvent event =
        phase == Phase.NONE
            ? Events.scheduled(seq, now, actor.value(), startsAt)
            : Events.rescheduled(seq, now, actor.value(), state.startsAt(), startsAt);
    commit(
        List.of(event),
        actor.value(),
        () -> {
          Optional<String> refusal = d.port().auctionRefusal(roomId);
          if (refusal.isPresent()) {
            throw new AuctionConflictException(AuctionConflictException.NOT_READY, refusal.get());
          }
          if (phase == Phase.NONE) {
            d.port().markScheduled(roomId, actor);
          }
        });
    return state;
  }

  private void doTick() {
    Instant now = now();
    resumeIfPending(now);
    switch (state.phase()) {
      case SCHEDULED -> {
        if (state.startRefusal() == null && !now.isBefore(state.startsAt())) {
          goLive(now);
        }
      }
      case LIVE -> advanceTimers(now);
      default -> {}
    }
  }

  /** After a restart: a lot whose deadline passed while the server was down gets a fresh T_bid. */
  private void resumeIfPending(Instant now) {
    if (!pendingResume) {
      return;
    }
    pendingResume = false;
    List<AuctionEvent> resume = TimerRules.resume(state, now);
    if (!resume.isEmpty()) {
      commit(resume, scheduledBy, null);
    }
  }

  private void advanceTimers(Instant now) {
    for (int i = 0; i < MAX_STEPS; i++) {
      List<AuctionEvent> due = TimerRules.due(state, now);
      if (due.isEmpty()) {
        return;
      }
      commit(due, scheduledBy, null);
    }
  }

  /** The outcome of a go-live attempt: a refusal reason, or the events that started the auction. */
  private record Start(String refusal, List<AuctionEvent> events, AuctionState next) {}

  private void goLive(Instant now) {
    Start start;
    try {
      start = d.tx().execute(status -> startInTransaction(now));
    } catch (RoomConflictException e) {
      refuse(now, e.getMessage());
      return;
    } catch (DataAccessException | TransactionException e) {
      throw new LogWriteException(e);
    }
    if (start.refusal() != null) {
      refuse(now, start.refusal());
      return;
    }
    applied(start.events(), start.next(), scheduledBy);
  }

  /**
   * Freezes the room into the auction in one transaction: the room moves to live, the lot cards are
   * stored, and the snapshot event and the first lot are logged. The room row stays locked from the
   * first read, so pool and config writes wait for it.
   */
  private Start startInTransaction(Instant now) {
    AuctionInputsDto inputs = d.port().liveInputs(roomId);
    Optional<String> refusal = d.port().auctionRefusal(roomId);
    if (refusal.isPresent()) {
      return new Start(refusal.get(), null, null);
    }
    d.lifecycle().advance(roomId, RoomState.AUCTION_LIVE, new UserId(scheduledBy));

    List<Lot> lots = new ArrayList<>();
    for (AuctionInputsDto.Player p : inputs.players()) {
      lots.add(
          new Lot(
              lots.size() + 1,
              p.id(),
              p.name(),
              p.country(),
              p.role(),
              p.tags(),
              p.set(),
              p.position(),
              p.basePrice()));
    }
    d.lots().insertAll(roomId, lots);

    Map<UUID, Long> purses = new LinkedHashMap<>();
    inputs.franchises().forEach(f -> purses.put(f.id(), inputs.config().purse()));
    List<Step> ladder =
        inputs.config().incrementLadder().stream().map(s -> new Step(s.from(), s.step())).toList();
    int open = inputs.config().openSeconds();
    long seq = state.lastSeq() + 1;
    List<AuctionEvent> events =
        List.of(
            Events.auctionStarted(
                seq,
                now,
                ladder,
                open,
                inputs.config().bidSeconds(),
                inputs.config().gapSeconds(),
                purses,
                lots.stream().map(Lot::playerId).toList()),
            Events.lotOpened(seq + 1, now, 1, now.plus(Duration.ofSeconds(open))));

    AuctionState.Builder frozen = state.edit();
    frozen.catalog = byPlayer(lots);
    AuctionState next = AuctionReducer.replay(frozen.build(), events);
    store(events, next, scheduledBy);
    return new Start(null, events, next);
  }

  private static Map<UUID, Lot> byPlayer(List<Lot> lots) {
    Map<UUID, Lot> catalog = new LinkedHashMap<>();
    lots.forEach(l -> catalog.put(l.playerId(), l));
    return catalog;
  }

  /** The room cannot go live now: log it once, stay scheduled, wait for a reschedule. */
  private void refuse(Instant now, String reason) {
    log.info("Auction {} start refused: {}", roomId, reason);
    commit(List.of(Events.startRefused(state.lastSeq() + 1, now, reason)), scheduledBy, null);
  }

  // ---- persisting ----

  /**
   * Persists {@code events} and applies them. {@code txWork} goes first in the same transaction
   * (room changes that must stand or fall with the events). On any failure to write, nothing is
   * applied or broadcast and the state stays as it was.
   */
  private void commit(List<AuctionEvent> events, UUID by, RoomWork txWork) {
    AuctionState next = AuctionReducer.replay(state, events);
    try {
      d.tx()
          .executeWithoutResult(
              status -> {
                if (txWork != null) {
                  txWork.apply();
                }
                store(events, next, by);
              });
    } catch (DataAccessException | TransactionException e) {
      throw new LogWriteException(e);
    }
    applied(events, next, by);
  }

  /** Appends the events and moves the header, inside the caller's transaction. */
  private void store(List<AuctionEvent> events, AuctionState next, UUID by) {
    for (AuctionEvent e : events) {
      d.events().append(roomId, e);
    }
    AuctionHeader header =
        new AuctionHeader(
            roomId, next.phase(), next.startsAt(), by, refusedAt(events, next), next.lastSeq());
    if (!d.headers().upsert(header, state.lastSeq(), now())) {
      throw new LogWriteException("The auction log moved under this writer");
    }
  }

  private Instant refusedAt(List<AuctionEvent> events, AuctionState next) {
    Instant at = startRefusedAt;
    for (AuctionEvent e : events) {
      if (e.kind() == EventKind.START_REFUSED) {
        at = e.at();
      }
    }
    return next.startRefusal() == null ? null : at;
  }

  private void applied(List<AuctionEvent> events, AuctionState next, UUID by) {
    this.state = next;
    this.scheduledBy = by;
    this.startRefusedAt = refusedAt(events, next);
    if (events.stream().anyMatch(e -> e.kind() == EventKind.AUCTION_STARTED)) {
      d.readiness().clear(roomId);
    }
    d.broadcaster().events(roomId, events, next);
  }
}
