package com.paddleup.auction.service;

import com.paddleup.auction.dto.AuctionConfigDto;
import com.paddleup.auction.dto.AuctionEventDto;
import com.paddleup.auction.dto.AuctionStateDto;
import com.paddleup.auction.dto.IntentResultDto;
import com.paddleup.auction.dto.LastResultDto;
import com.paddleup.auction.dto.LotDto;
import com.paddleup.auction.dto.SquadEntryDto;
import com.paddleup.auction.dto.StandingDto;
import com.paddleup.auction.dto.StepDto;
import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.EventKind;
import com.paddleup.auction.model.Lot;
import com.paddleup.auction.model.Outcome;
import com.paddleup.auction.model.Phase;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Turns model types into the dtos that leave the module. */
final class AuctionViews {

  private AuctionViews() {}

  static LotDto lot(Lot l) {
    return l == null
        ? null
        : new LotDto(
            l.no(),
            l.playerId(),
            l.name(),
            l.country(),
            l.role(),
            l.tags(),
            l.set(),
            l.position(),
            l.basePrice());
  }

  static AuctionStateDto state(AuctionState s, Instant serverNow, Collection<UUID> connected) {
    boolean started = s.phase() == Phase.LIVE || s.phase() == Phase.ENDED;
    AuctionConfigDto config =
        started
            ? new AuctionConfigDto(
                s.openSeconds(),
                s.bidSeconds(),
                s.gapSeconds(),
                s.ladder().stream().map(x -> new StepDto(x.from(), x.step())).toList())
            : null;
    List<StandingDto> standings =
        s.purses().entrySet().stream()
            .map(
                e ->
                    new StandingDto(
                        e.getKey(),
                        e.getValue(),
                        s.squads().getOrDefault(e.getKey(), List.of()).stream()
                            .map(x -> new SquadEntryDto(x.lot(), x.playerId(), x.price()))
                            .toList()))
            .toList();
    return new AuctionStateDto(
        s.phase().value(),
        s.startsAt(),
        s.startRefusal(),
        serverNow,
        s.lastSeq(),
        config,
        s.lots().size(),
        lot(s.lot()),
        s.lotOpen(),
        s.currentBid(),
        s.leader(),
        s.deadline(),
        List.copyOf(s.passedBy()),
        standings,
        s.unsold(),
        s.nextOpenAt(),
        s.lastResult() == null
            ? null
            : new LastResultDto(
                s.lastResult().lot(),
                s.lastResult().sold(),
                s.lastResult().franchiseId(),
                s.lastResult().amount()),
        lot(s.nextLot()),
        List.copyOf(connected));
  }

  /**
   * An event as members see it. A lot_opened event also carries the lot's card, so a client needs
   * nothing else to show the lot. {@code state} is any state at or after the event.
   */
  static AuctionEventDto event(AuctionEvent e, AuctionState state, Instant serverNow) {
    Map<String, Object> payload = e.payload();
    if (e.kind() == EventKind.LOT_OPENED && e.lot() != null && e.lot() <= state.lots().size()) {
      payload = new LinkedHashMap<>(payload);
      payload.put("card", lot(state.lots().get(e.lot() - 1)));
    }
    return new AuctionEventDto(
        e.seq(), e.at(), e.kind().value(), e.franchiseId(), e.lot(), payload, serverNow);
  }

  static IntentResultDto result(Outcome o, Instant serverNow) {
    return new IntentResultDto(
        o.accepted() ? "accepted" : "rejected",
        o.reason(),
        o.seq(),
        o.lot(),
        o.amount(),
        o.deadline(),
        serverNow);
  }
}
