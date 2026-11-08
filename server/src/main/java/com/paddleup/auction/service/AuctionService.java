package com.paddleup.auction.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.auction.config.AuctionProperties;
import com.paddleup.auction.dto.AuctionEventDto;
import com.paddleup.auction.dto.AuctionStateDto;
import com.paddleup.auction.dto.BidRequest;
import com.paddleup.auction.dto.EventsDto;
import com.paddleup.auction.dto.IntentResultDto;
import com.paddleup.auction.dto.PassRequest;
import com.paddleup.auction.dto.ScheduleRequest;
import com.paddleup.auction.dto.TicketDto;
import com.paddleup.auction.exception.AuctionBusyException;
import com.paddleup.auction.exception.AuctionNotFoundException;
import com.paddleup.auction.exception.AuctionRequestException;
import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Intent;
import com.paddleup.auction.model.Phase;
import com.paddleup.auction.repository.AuctionEventRepository;
import com.paddleup.rooms.dto.RoomAccessDto;
import com.paddleup.rooms.exception.RoomForbiddenException;
import com.paddleup.rooms.service.AuctionRoomPort;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

/**
 * What the controller calls. It checks who the caller is in the room through the rooms port, then
 * hands the work to the room's single-writer runner. Clients send intents only: the franchise, the
 * time and every outcome come from the server.
 */
@Service
public class AuctionService {

  private final AuctionRoomPort port;
  private final AuctionRegistry registry;
  private final AuctionEventRepository events;
  private final Presence presence;
  private final StompTicketService tickets;
  private final AuctionProperties props;
  private final Clock clock;

  AuctionService(
      AuctionRoomPort port,
      AuctionRegistry registry,
      AuctionEventRepository events,
      Presence presence,
      StompTicketService tickets,
      AuctionProperties props,
      Clock clock) {
    this.port = port;
    this.registry = registry;
    this.events = events;
    this.presence = presence;
    this.tickets = tickets;
    this.props = props;
    this.clock = clock;
  }

  /** Schedules or reschedules the start. Commissioner only. */
  public AuctionStateDto schedule(UserId caller, UUID roomId, ScheduleRequest request) {
    RoomAccessDto access = port.access(roomId, caller);
    if (!access.commissioner()) {
      throw new RoomForbiddenException("Only the Commissioner can do this");
    }
    if (request == null || request.startsAt() == null) {
      throw new AuctionRequestException("startsAt", "A start time is required");
    }
    AuctionState state = await(registry.runner(roomId).schedule(caller, request.startsAt()));
    return AuctionViews.state(state, clock.instant(), presence.connected(roomId));
  }

  /** Full state for a member; 404 until an auction is scheduled. */
  public AuctionStateDto state(UserId caller, UUID roomId) {
    port.access(roomId, caller);
    AuctionState state = registry.runner(roomId).state();
    if (state.phase() == Phase.NONE) {
      throw new AuctionNotFoundException();
    }
    return AuctionViews.state(state, clock.instant(), presence.connected(roomId));
  }

  /** Events with a seq above {@code after}, at most {@code limit}. */
  public EventsDto events(UserId caller, UUID roomId, long after, Integer limit) {
    port.access(roomId, caller);
    int max = props.eventsPageMax();
    int page = limit == null ? max : Math.max(1, Math.min(limit, max));
    if (after < 0) {
      throw new AuctionRequestException("after", "Must be 0 or more");
    }
    AuctionState state = registry.runner(roomId).state();
    List<AuctionEvent> found = events.after(roomId, after, page + 1);
    boolean more = found.size() > page;
    var now = clock.instant();
    List<AuctionEventDto> dtos =
        found.stream().limit(page).map(e -> AuctionViews.event(e, state, null)).toList();
    long last = dtos.isEmpty() ? Math.max(after, 0) : dtos.get(dtos.size() - 1).seq();
    return new EventsDto(dtos, last, more, now);
  }

  public IntentResultDto bid(UserId caller, UUID roomId, BidRequest request) {
    if (request == null
        || request.intentId() == null
        || request.lot() == null
        || request.amount() == null) {
      throw new AuctionRequestException("body", "intentId, lot and amount are required");
    }
    if (request.lot() < 1 || request.amount() < 1) {
      throw new AuctionRequestException("body", "lot and amount must be positive");
    }
    RoomAccessDto access = port.access(roomId, caller);
    var intent =
        Intent.bid(
            request.intentId(),
            caller.value(),
            access.franchiseId(),
            request.lot(),
            request.amount());
    return AuctionViews.result(await(registry.runner(roomId).intent(intent)), clock.instant());
  }

  public IntentResultDto pass(UserId caller, UUID roomId, PassRequest request) {
    if (request == null || request.intentId() == null || request.lot() == null) {
      throw new AuctionRequestException("body", "intentId and lot are required");
    }
    if (request.lot() < 1) {
      throw new AuctionRequestException("body", "lot must be positive");
    }
    RoomAccessDto access = port.access(roomId, caller);
    var intent =
        Intent.pass(request.intentId(), caller.value(), access.franchiseId(), request.lot());
    return AuctionViews.result(await(registry.runner(roomId).intent(intent)), clock.instant());
  }

  /** A single-use ticket to open the socket without a session cookie. Members only. */
  public TicketDto ticket(UserId caller, UUID roomId) {
    port.access(roomId, caller);
    return tickets.issue(caller);
  }

  private <T> T await(CompletableFuture<T> future) {
    try {
      return future.get(props.intentTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException(cause);
    } catch (TimeoutException e) {
      throw new AuctionBusyException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AuctionBusyException(e);
    }
  }
}
