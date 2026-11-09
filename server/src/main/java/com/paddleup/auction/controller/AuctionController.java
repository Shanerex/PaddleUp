package com.paddleup.auction.controller;

import com.paddleup.accounts.service.CurrentUser;
import com.paddleup.auction.dto.AuctionStateDto;
import com.paddleup.auction.dto.BidRequest;
import com.paddleup.auction.dto.EventsDto;
import com.paddleup.auction.dto.IntentResultDto;
import com.paddleup.auction.dto.PassRequest;
import com.paddleup.auction.dto.PresenceDto;
import com.paddleup.auction.dto.ReadyRequest;
import com.paddleup.auction.dto.ScheduleRequest;
import com.paddleup.auction.dto.TicketDto;
import com.paddleup.auction.service.AuctionService;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The auction through REST. Bids and passes are intents: the client names a lot and an amount, and
 * the server decides. There is no cancel or undo route, because bids are irrevocable.
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/auction")
class AuctionController {

  private final CurrentUser currentUser;
  private final AuctionService auction;

  AuctionController(CurrentUser currentUser, AuctionService auction) {
    this.currentUser = currentUser;
    this.auction = auction;
  }

  @PutMapping(value = "/schedule", consumes = MediaType.APPLICATION_JSON_VALUE)
  AuctionStateDto schedule(@PathVariable UUID roomId, @RequestBody ScheduleRequest request) {
    return auction.schedule(currentUser.require(), roomId, request);
  }

  @GetMapping
  AuctionStateDto state(@PathVariable UUID roomId) {
    return auction.state(currentUser.require(), roomId);
  }

  @GetMapping("/events")
  EventsDto events(
      @PathVariable UUID roomId,
      @RequestParam(defaultValue = "0") long after,
      @RequestParam(required = false) Integer limit) {
    return auction.events(currentUser.require(), roomId, after, limit);
  }

  @PostMapping(value = "/bids", consumes = MediaType.APPLICATION_JSON_VALUE)
  IntentResultDto bid(@PathVariable UUID roomId, @RequestBody BidRequest request) {
    return auction.bid(currentUser.require(), roomId, request);
  }

  @PostMapping(value = "/passes", consumes = MediaType.APPLICATION_JSON_VALUE)
  IntentResultDto pass(@PathVariable UUID roomId, @RequestBody PassRequest request) {
    return auction.pass(currentUser.require(), roomId, request);
  }

  @PutMapping(value = "/ready", consumes = MediaType.APPLICATION_JSON_VALUE)
  PresenceDto ready(@PathVariable UUID roomId, @RequestBody ReadyRequest request) {
    return auction.ready(currentUser.require(), roomId, request);
  }

  @PostMapping("/ticket")
  TicketDto ticket(@PathVariable UUID roomId) {
    return auction.ticket(currentUser.require(), roomId);
  }
}
