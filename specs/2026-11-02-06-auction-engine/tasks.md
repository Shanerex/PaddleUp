---
status: final
---
# Tasks: 2026-11-02-06-auction-engine

Design refs: none. This is a server-only spec; the auction screen is spec 07.

## Tasks
- [x] T-1 (AC-11): Add `V8__auction.sql` with the `auctions` table (incl. start_refused_at), `auction_events` and `auction_lots`. Include the PK, the unique intent index, and append-only triggers on events and lots. Test: SQL UPDATE, DELETE and TRUNCATE on the log are rejected, and a duplicate (room_id, seq) fails.
- [x] T-2 (AC-4): Add `auction.util.Ladder` for the first bid (base price) and the next bid (current + the step of the band holding it). Unit test a two-band ladder at base, below, at and above the band edge.
- [x] T-3 (AC-3, AC-7, AC-8): In rooms, add nullable `openSeconds`, `bidSeconds` and `gapSeconds` to `RoomConfigDto`. Null reads as 20/10/5 s. `RoomConfigValidator` checks the ranges 5-120, 3-60 and 0-30, and `RoomConfigFactory` sets the defaults; there is no pack change. Test: a stored config without the fields reads the defaults, and out-of-range values return 422.
- [x] T-4 (AC-1, AC-2, AC-17): In rooms, add the public `AuctionRoomPort` (access, auctionRefusal, liveInputs) with `RoomAccessDto` and `AuctionInputsDto`. Test the membership roles (404 for a non-member, 403 for a non-Commissioner), the three refusals (no pool copy, pool below minimum, too few franchises), and player order (set order, then position).
- [x] T-5 (AC-8, AC-14): Add `auction.model` (state, events, intents) and the pure `AuctionReducer`. Unit test that a scripted event list rebuilds lot, price, leader, purses, squads, unsold, start refusal and lastSeq.
- [x] T-6 (AC-4, AC-5, AC-6): Add the pure `IntentRules`, plus `BidGuard` with `PurseGuard`. Unit test that bids by the leader, a non-franchise, a franchise short of purse, a franchise after its own pass, or at a wrong amount are rejected with a reason and no event. A pass leaves the deadline and lot unchanged and bars only that lot.
- [x] T-7 (AC-11, AC-12): Add the Spring JDBC repositories: event append with an explicit seq, `after(seq, limit)`, intent lookup, the header and the lots. Testcontainers test that seq stays gap-free across appends and after a failed insert.
- [x] T-8 (AC-9, AC-10, AC-11): Add `AuctionRunner` and `AuctionRegistry` (single-thread executor; persist, then apply, broadcast, reply; bounded rejection cache). Tests:
  - A forced log-write failure returns an error, broadcasts nothing and leaves state unchanged.
  - 20 rounds of concurrent same-amount bids give exactly one acceptance per round.
  - A repeated intent id returns the same outcome and writes one event.
- [x] T-9 (AC-1): Add PUT /schedule for schedule and reschedule. It moves the room to auction_scheduled through `RoomLifecycle.advance` and logs the `scheduled` and `rescheduled` events. Tests:
  - A future start is accepted.
  - A past start returns 400.
  - A non-Commissioner member gets 403.
  - With no pool copy or too small a pool, the request is refused and the room stays unscheduled.
  - The lobby state carries startsAt and serverNow.
- [x] T-10 (AC-3, AC-8): Add `AuctionTicker`, `GoLive` and the lot cycle. Tests with a mutable Clock and a manual tick:
  - The auction goes live at the start with no request.
  - `auction_started` is the first live-phase event and holds ladder, T_open, T_bid, G, purses and lot order.
  - Lot 1 is the first player of the first set, with deadline = open + T_open.
  - At the deadline the leader wins and is debited, or the lot is unsold.
  - The next lot opens after G, and the last close ends the auction with unsold players listed.
- [x] T-11 (AC-17): Handle a refused start. At the start instant, a pool below minimum or too few franchises opens no lot, keeps the room scheduled, and logs exactly one `start_refused` with the reason, with no retry on later ticks. Lobby state shows the reason. After the fix and a reschedule, the auction goes live.
- [x] T-12 (AC-2): Integration test: while scheduled, pool, set-order and config writes succeed, as does a reschedule. After go-live each returns 409 and changes nothing.
- [x] T-13 (AC-4, AC-5, AC-6, AC-7, AC-10): Add `AuctionController` POST /bids and POST /passes, their dtos, and `AuctionExceptionHandler`. A rule rejection returns 200 rejected with a reason; a phase error returns 409 not_live, no_open_lot or ended per the matrix. Tests:
  - A bid after the deadline gets `lot_closed`.
  - An accepted bid sets deadline = server now + T_bid.
  - A client with a clock one hour off sees the same deadline and the same outcomes.
  - No cancel or undo route exists.
- [x] T-14 (AC-12, AC-13): Add GET / (full state: phase, lot, bid, leader, deadline, purses, squads, lastSeq, serverNow, start refusal, presence) and GET /events?after=N. Tests: from seq N the reply holds N+1 onward, and a non-member gets 404.
- [x] T-15 (AC-12, AC-13): Add STOMP:
  - the starter-websocket dependency and `WebSocketConfig`;
  - `StompAuth` with two paths: the session principal from a same-origin handshake, or a single-use 30 s ticket from POST /ticket presented on CONNECT;
  - `StompAuthInterceptor`, the SecurityConfig permit for the `/api/ws/**` handshake, `AuctionBroadcaster` (sends after commit) and `Presence`.

  Tests run with a Java STOMP client on both paths:
  - A non-member cannot subscribe.
  - CONNECT with neither a session nor a valid ticket is refused.
  - A reused or expired ticket is refused.
  - SEND is refused.
  - Events carry seq.
  - Lobby presence shows connected franchises.
- [x] T-16 (AC-13): Resync test. A reconnecting client and a new tab get state equal to the server's. A client that drops one event detects the seq gap and refetches to equal state.
- [x] T-17 (AC-14, AC-17): Add `AuctionRecovery` on ApplicationReadyEvent. Test by running a second application context on the same DB, mid-lot with bids. Check that:
  - lot, price, leader, purses and seq match, with no event lost or duplicated;
  - a deadline passed in downtime logs `lot_resumed` and a fresh T_bid;
  - lobby and gap states resume;
  - a start passed in downtime goes live, or is refused once (and stays refused across a second restart).
- [x] T-18 (AC-15): Latency test with 10 STOMP subscribers and 100 bids: p95 bid-to-broadcast is under 500 ms (local profile tag).
- [x] T-19 (AC-16): Run a scripted auction to ended with the stub football pack and a 20-player fake pool, with both sold and unsold lots. `scripts/check-agnostic.sh` passes, and no main code changes for the pack.
- [x] T-20 (AC-11, AC-14, AC-17): Docs: the four decision records named in plan.md, the approved architecture.md edits from plan.md, and a README section on the auction API, the STOMP topics and the two connection modes.
