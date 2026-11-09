---
status: final
---
# Plan: 2026-11-02-07-auction-screen

## Approach
One route, `/rooms/[roomId]/auction`, renders the lobby (phase `scheduled`) or the A Spotlight lot screen (phase `live`) from one server-fed store, so R-3/AC-3 is a re-render and needs no navigation. All truth comes from `GET /api/rooms/{id}/auction` (AuctionStateDto) plus STOMP broadcasts. Events patch only bid, leader, deadline and `nextBid`; structural events and seq gaps trigger a full state refetch (R-10, AC-13 to AC-15). Time left = server instant minus a server-clock offset taken from `serverNow` samples on a monotonic clock (R-6, AC-1, AC-6). Bidding is gated on `synced && connected && canBid` (AC-7, AC-15, AC-17). P-1 (T-1) is drawn and approved before any task that renders an undrawn state. P-2 is confirmed: engine 06 has presence but no ready intent, so readiness is added in-memory (Q1=A).

## Options considered
- Ready (P-2, R-2): (A, chosen) in-memory `Readiness` beside `Presence`, `PUT .../auction/ready`, sent on the presence topic: no schema change, but a restart clears it. (B) Log `ready_set`/`ready_cleared` events: survives a restart, but puts lobby noise in the auction log and adds event kinds. (C) Drop R-2: lobby shows IN LOBBY / NOT HERE only, which needs a requirements change.
- Next bid amount (R-7): (A, chosen) add `nextBid` to AuctionStateDto and to the `lot_opened`/`bid_accepted` broadcasts via `Ladder.required`: a DTO-only change with one source of truth; the client never computes it. (B) The client mirrors `Ladder` over the frozen `config.ladder`: no server change, but the logic is duplicated and can drift.
- Paused/adjourned (R-11, AC-16): engine 06 has no such phase or event, and FR-47 is out of scope. (A, chosen) Build the 3d/3e notices against fixture phases `paused`/`adjourned`, make any unknown phase lock bidding, and defer their e2e to the Commissioner-controls spec (FR-47). (B) Drop them from this spec.

## Design
- Server (auction module):
  - `auction.service.Readiness`: in-memory map per room, set only in SCHEDULED by a franchise, cleared on `auction_started`.
  - `AuctionController PUT /ready {ready}` returns `PresenceDto{connected, ready}`. Outside SCHEDULED it answers 409 `not_scheduled` (a new `Reason` code); a caller with no franchise gets 409 `not_a_franchise`.
  - `PresenceDto` and `AuctionStateDto` gain `ready: List<UUID>`.
  - `AuctionStateDto.nextBid`, plus `nextBid` in the `lot_opened`/`bid_accepted` payload in `AuctionViews.event`.
  - `WebSocketConfig`: simple-broker heartbeats 1000/1000 with a `ThreadPoolTaskScheduler` (needed for AC-13 on silent drops).
- API types: regenerate `web/src/api/generated.ts`. `web/src/api/client.ts` gains `getAuction`, `bid`, `setReady` and `getTicket`, and the types `AuctionState`, `AuctionEvent`, `IntentResult`.
- New `web/src/auction/`:
  - `clock.ts`: offset from the min-RTT `serverNow` sample, on `performance.now`.
  - `live.ts`: ticket, then `@stomp/stompjs` connect to `NEXT_PUBLIC_WS_URL` (`/api/ws`). Subscribes to the topic and `/presence`, buffers, fetches state, drops seq <= lastSeq. Resyncs on connect, on `visibilitychange`, on a seq gap and on `lot_sold`/`lot_unsold`/`lot_resumed`/`auction_started`/`auction_ended`. `online`/`offline` plus heartbeat loss give `reconnecting`.
  - `view.ts`: a pure state-to-view model holding status (an unknown phase maps to locked), my status, `canBid` and its cause.
  - `useBid.ts`: one in-flight intent, a 300 ms guard and a stable `intentId` on retry; the amount sent is the server's `nextBid`.
  - `copy.ts`: reason codes to the mock copy.
- New `web/src/app/rooms/[roomId]/auction/`:
  - `page.tsx` and `auction-view.tsx` (the phase switch).
  - `lobby.tsx` (Room `lobby`).
  - `lot-card.tsx` (A Spotlight).
  - `bid-dock.tsx` (AuctionBidDock, with a fixed lifeline slot that stays empty and inert).
  - `notices.tsx` (AuctionNotices 3a/3b/3d/3e/3f/3g/3h).
  - `auction.module.css` (tokens only; DM Mono tabular, fixed-size number boxes).
- Labels come from the room's pack labels via `rooms/pool.ts` `roleLabel`/`tagLabel` and money via `rooms/money.ts` `formatMoney` (R-5). Photo and last-season points are in no data today; the card renders them only when present (AC-4). Franchise names and hues come from `getRoom`/`getMembers`.
- `room-view.tsx` links to the auction route when the room state is `auction_scheduled`/`auction_live`.
- Playwright specs live under the web e2e directory. They use a seeded room, two browser contexts and the stub football pack.
- New web dependency: `@stomp/stompjs`.

## Architecture changes
- Auction engine: lobby readiness is in-memory and unlogged like presence. It is sent on `/topic/auctions/{roomId}/presence` and set by `PUT .../auction/ready` only while scheduled. `AuctionStateDto` and `PresenceDto` carry `ready`.
- Realtime: the STOMP simple broker sends heartbeats every 1 s each way, and a client treats 2.5 s of silence as a drop.
- The auction state and the `lot_opened`/`bid_accepted` broadcasts carry `nextBid`, which the web never computes.
- Web: a new `web/src/auction/` holds client sync. The socket goes straight to the server (`NEXT_PUBLIC_WS_URL`) with a ticket. Bidding is enabled only after a full state fetch on connect, focus, seq gap or lot close. Events patch only bid, leader, deadline and `nextBid`.

## Risks
- A restart clears readiness: managers re-tap. The lobby re-reads `ready` on resync.
- The ticket endpoint may refuse before go-live: T-2 checks it works in SCHEDULED, since the lobby needs presence.
- A refetch per lot close under load: one GET per lot per client is small, and the state is served from the runner's memory.
- The `paused`/`adjourned` contract is guessed until FR-47: the notices take a mapped status rather than the raw phase, and the unknown-phase lock is the safety net.
- E2E bid broadcasts need a second actor: Playwright uses a second context signed in as another manager, with tick timing set short in the test profile.
- Reduced motion and number roll: the CSS `prefers-reduced-motion` turns off roll and stamp and keeps fades (AC-18).
