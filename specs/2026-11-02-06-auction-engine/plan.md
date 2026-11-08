---
status: final
---
# Plan: 2026-11-02-06-auction-engine

## Approach
- One single-writer runner per unended auction, held in memory, applies every intent and timer step one at a time (R-9).
- The append-only `auction_events` log is the only source of truth. Each change is persisted, then applied, then broadcast, then replied to (R-11). On boot, state is rebuilt by replaying the log (R-14).
- A pure reducer (event -> state) and pure intent rules (state + intent + now -> event or rejection) hold all auction logic. Both are plain Java, unit-tested without Spring (R-4..R-8).
- A 100 ms scheduler tick drives start, deadline and gap (R-2, R-8). Every intent first closes any lot whose deadline has passed, so `lot_closed` is exact however late the tick runs (R-8).
- Rooms stays the owner of room state, pool and config. Auction reads them through one new public port and goes live through `RoomLifecycle.advance`. If go-live is refused at the start, the auction stays scheduled with one `start_refused` event (R-2). The 409 lock on pool and config writes after go-live already exists (R-1, R-2).
- Intents are REST POSTs. STOMP carries broadcasts only and refuses inbound SEND (R-15). STOMP is reachable same-origin with the session cookie, or directly with a single-use ticket; both paths go through one auth check. Every event and state reply carries `seq` and `serverNow` (R-7, R-13).

## Options considered
1. (chosen) In-memory single-writer runner per auction, log replayed on boot, tick-driven timer, seq assigned by the runner. Trade-off: no locks, arrival order is queue order, and a test Clock drives the timers easily; but auctions must run on one server instance.
2. DB-centric: SELECT FOR UPDATE on the auction row for each intent, mutable state tables beside the log, DB-polled timers. Trade-off: safe across several instances, but there are two sources of truth that can drift and more SQL per bid.
3. A per-auction ReentrantLock plus exact-deadline ScheduledExecutor tasks, cancelled on each bid. Trade-off: precise timers, but cancel/fire races, and a test Clock struggles to drive them (AC-3, AC-8, AC-14).

## Design
Server module `com.paddleup.auction` (MVCS plus exception/config/util):

**Migration `server/src/main/resources/db/migration/V8__auction.sql`**
- `auctions`: room_id PK FK rooms, phase text CHECK in scheduled/live/ended, starts_at timestamptz, scheduled_by uuid, start_refused_at timestamptz null, last_seq bigint, created_at, updated_at. It is an index for boot discovery, updated in the same transaction as each event.
- `auction_events`: room_id, seq bigint, occurred_at timestamptz, kind text, franchise_id uuid null, lot_no int null, actor_user_id uuid null, intent_id uuid null, payload jsonb.
  - PK (room_id, seq) keeps seq gap-free and makes a second writer fail.
  - Unique (room_id, actor_user_id, intent_id) WHERE intent_id IS NOT NULL.
  - Triggers block UPDATE, DELETE and TRUNCATE, the same shape as room_log.
- `auction_lots`: room_id, lot_no, pool_player_id, set_name, position_in_set, name, country, role_key, tag_keys text[], base_price bigint; PK (room_id, lot_no). Card fields are frozen at go-live and append-only (same triggers).

**Event kinds** (`auction.model.EventKind`)

| Kind | Payload |
|---|---|
| scheduled | startsAt |
| rescheduled | from, to |
| start_refused | reason |
| auction_started | ladder, openSeconds, bidSeconds, gapSeconds, purses per franchise, lot order as pool player ids |
| lot_opened | lot, deadline |
| bid_accepted | lot, franchise, amount, deadline |
| passed | lot, franchise |
| lot_sold | lot, franchise, amount |
| lot_unsold | lot |
| lot_resumed | lot, deadline |
| auction_ended | none |

- `auction_started` is the first event of the live phase; scheduling events come before it (AC-3, accepted).
- Lifeline kinds (FR-48) are reserved and will carry type, lot and time only.

**Start refused (R-2)**
- At the start instant, GoLive asks `AuctionRoomPort.auctionRefusal`.
- When the room refuses (no pool copy, a pool too small, or too few franchises), the runner logs one `start_refused` and sets `start_refused_at`. It does not retry, the room stays `auction_scheduled`, and the lobby state shows the reason.
- A reschedule to a future start clears the flag (`rescheduled` event). Pool, set-order and config writes stay open.

**model**
- `AuctionState`: phase, startsAt, startRefusal, lots, currentLot, currentBid, leader, deadline, passedBy, purses, squads, unsold, nextOpenAt, lastSeq.
- `AuctionEvent`, `Intent` (Bid/Pass with intentId, actor, franchise, lot, amount) and `Outcome`.

**util**
- `Ladder`: the first bid is the base price; each next bid is current + the step of the band with the largest `from` <= current (AC-4).

**service**
- `AuctionReducer`: pure apply.
- `IntentRules`: pure decide. Rule rejections return HTTP 200 {outcome: rejected, reason}, with reason one of not_a_franchise, leader, passed, purse, outbid, lot_closed. Phase errors return 409 with code not_live, no_open_lot or ended (accepted).
- `BidGuard` interface plus `PurseGuard`. This is the seam for FR-44: 2026-11-02-08 adds a guard bean (R-5).
- `AuctionRunner`: one single-thread executor per room, and `submit(...)` returns a future.
  - Persists through a TransactionTemplate.
  - Keeps a bounded LRU of rejected intent outcomes.
  - Finds accepted repeats by intent id in the log (R-10).
- `AuctionRegistry`: roomId -> runner.
- `AuctionTicker`: @Scheduled every `paddleup.auction.tick` (default 100ms). A value of 0 turns it off, and tests then call `tickAll()` with a mutable Clock.
- `GoLive`: one transaction that runs `RoomLifecycle.advance(AUCTION_LIVE, scheduledBy)`, inserts `auction_lots`, then writes `auction_started` and `lot_opened`. A refusal is handled as described above.
- `AuctionRecovery`: on ApplicationReadyEvent, loads every auction whose phase is not ended, replays it, then ticks once. That one tick covers a start passed in downtime, `lot_resumed` with a fresh T_bid, and a gap that continues (R-14). A refused start stays refused.
- `AuctionBroadcaster`: SimpMessagingTemplate to `/topic/auctions/{roomId}`, sent only after commit.
- `Presence`: STOMP session events -> connected franchises, on `/topic/auctions/{roomId}/presence`. It carries no seq and is not logged.
- `AuctionService`: controller facade. Commissioner, member and franchise checks go through the rooms port.
- `StompAuth`: the one auth check for both STOMP paths. It resolves the user from the handshake principal (session cookie) or from a `ticket` CONNECT header. Then a single `AuctionRoomPort.access` membership check runs per SUBSCRIBE.
- `StompTicketService`: single-use tickets valid for 30 s, kept in memory and bound to the user.

**repository** (Spring JDBC)
- `AuctionRepository`.
- `AuctionEventRepository`: append with explicit seq, `after(roomId, seq, limit)`, `byIntent`.
- `AuctionLotRepository`.

**controller** `AuctionController` under `/api/rooms/{roomId}/auction`

| Method and path | Body or query | Returns |
|---|---|---|
| `PUT /schedule` | {startsAt} | |
| `GET /` | | state incl. serverNow, lastSeq, startRefusal, presence |
| `GET /events` | `?after=N&limit<=500` | |
| `POST /bids` | {intentId, lot, amount} | |
| `POST /passes` | {intentId, lot} | |
| `POST /ticket` | | {ticket, expiresAt} |

- No cancel or undo route exists (R-10).
- Non-members get 404 on every route and on SUBSCRIBE, matching rooms (Q4).
- A member who is not the Commissioner gets 403 on schedule.

**dto**
- ScheduleRequest, BidRequest, PassRequest, IntentResultDto.
- AuctionStateDto, LotDto, StandingDto (purse, squad).
- AuctionEventDto (seq, at, kind, franchiseId, lot, payload; intent ids are omitted).
- TicketDto.

**exception**
- AuctionConflictException (409 with a code), AuctionRequestException (400), LogWriteException (503).
- AuctionExceptionHandler. Room errors from the port keep their own 404/403 mapping.

**config**
- `AuctionProperties`: tick, ticketTtl (30s).
- `WebSocketConfig`: endpoint `/api/ws`, allowed origin = web-base-url, simple broker `/topic` with preservePublishOrder, no application destinations, and a handshake handler that passes on any session principal.
- `StompAuthInterceptor`: calls `StompAuth`. CONNECT needs either a session principal or a valid ticket. SUBSCRIBE is allowed only to the user's own room topics. SEND is refused.
- `AuctionSchedulingConfig`: a module-private ThreadPoolTaskScheduler.

**Outside the auction package**
- `server/pom.xml`: add `spring-boot-starter-websocket`.
- `accounts.config.SecurityConfig`: permit the `/api/ws/**` handshake without a session, so the ticket path works; auth then happens at STOMP CONNECT.
- The web client picks the cookie or ticket path by config; it is built in spec 07.

Rooms module (used only through service/dto/exception):
- `rooms.dto.RoomConfigDto`: add nullable `openSeconds`, `bidSeconds` and `gapSeconds`. A null reads as the default (20, 10 and 5 s), so stored rooms need no migration.
- `RoomConfigValidator`: ranges 5-120, 3-60 and 0-30 s.
- `RoomConfigFactory`: sets the defaults. There is no pack change (Q2).
- New public `rooms.service.AuctionRoomPort`:
  - `access(roomId, user)` -> `rooms.dto.RoomAccessDto` (state, commissioner, franchiseId). Throws RoomNotFoundException (404) or RoomForbiddenException (403).
  - `auctionRefusal(roomId)` -> an Optional reason. It reuses the pool and manager checks of `RoomLifecycle`.
  - `liveInputs(roomId)` -> `rooms.dto.AuctionInputsDto`: config, franchises, set order, and players in set order then position. It is read with the room row locked in the caller's transaction.
- Scheduling a room in setup or retention calls `RoomLifecycle.advance(AUCTION_SCHEDULED, commissioner)`. A room already in `auction_scheduled` that has no auction row counts as "none".
- When the auction ends, the room stays `auction_live`. Moving it to season_live belongs to a later spec.

Tests (JUnit 5 + Testcontainers, under `server/src/test/java/com/paddleup/auction/...`):
- Unit tests for Ladder, IntentRules and AuctionReducer.
- An integration test per AC.
- A Java STOMP client (`WebSocketStompClient`) that runs on both auth paths.
- A second application context on the same container, for restart.
- The stub football pack plus a 20-player fake pool, for AC-16.
- There is no UI in this spec (that screen is 07), so there are no Design refs.

## Architecture changes
Approved answers are folded in; the implementer applies these to docs/knowledge/architecture.md in T-20.
1. New "Auction engine" section:
   - One single-writer runner per unended auction holds its state in memory.
   - `auction_events` (PK room_id+seq, append-only by trigger) is the only source of truth, replayed on boot. Each change is persisted, applied, broadcast, then replied to.
   - Auctions run on one server instance; a second writer fails on the seq PK and cannot fork the log.
   - A refused start logs `start_refused` and stays scheduled until the Commissioner reschedules.
2. Timers: the server Clock plus a 100 ms tick (`paddleup.auction.tick`). Intents close overdue lots lazily. Tests turn the tick off and step a mutable Clock.
3. Realtime: STOMP endpoint `/api/ws`, broadcasts only, origin allow-list = web-base-url. There are two ways in, and both go through one auth check (`StompAuth`):
   - same origin through the /api rewrite with the session cookie;
   - direct to the server origin with a single-use 30 s REST ticket on CONNECT, for Vercel, which does not proxy upgrades.

   The web client picks one by config. SecurityConfig permits the `/api/ws/**` handshake.
4. Rooms:
   - Config gains openSeconds, bidSeconds and gapSeconds (null = default 20, 10 and 5 s).
   - New public port `rooms.service.AuctionRoomPort`.
   - Auction endpoints follow the rooms access rule: 404 for non-members, 403 for non-Commissioners.
   - The room stays auction_live after the auction ends.
5. New decision records: auction-single-writer-runner, auction-event-log-schema, tick-driven-timers, stomp-dual-auth (cookie or ticket).

## Risks
- Two STOMP auth paths could drift. Mitigation: one `StompAuth` and one membership check, with the STOMP tests run on both paths.
- The anonymous `/api/ws` handshake is an open endpoint. Mitigation: CONNECT without a principal or a valid single-use ticket is refused, along with the origin allow-list and the 30 s ticket TTL.
- The simple broker may reorder sends. Mitigation: preservePublishOrder, and the client resyncs on any seq gap (AC-13).
- A resent rejected intent could be decided again after a restart and accepted (a first bid of 25 is rejected at 0, valid once 20 is bid). Mitigation: rejections are stored in `auction_rejections` and a resend returns the first answer (decision 2026-11-08-auction-rejections-persisted).
- A refused start leaves a room stuck in the lobby. Mitigation: the reason shows in the lobby, and a reschedule clears it.
- The tick adds up to 100 ms of close lag. Mitigation: the deadline still decides, because intents close overdue lots first; the broadcast lag stays within NFR-2.
- A large pool snapshot in one event would hit Jackson's 256 KB limit. Mitigation: card fields live in `auction_lots`, and the event holds only ids.
- The web config form might drop the new timer fields. Mitigation: the fields are nullable with defaults; the timer UI belongs to 09.
- An exception inside the runner. Mitigation: each message is wrapped, so state is unchanged, the caller gets an error and the runner keeps running.
- Multi-instance deploys are out of scope. Mitigation: the seq PK makes a second writer fail loudly.
