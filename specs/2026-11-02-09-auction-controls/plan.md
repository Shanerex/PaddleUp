---
status: final
---
# Plan: 2026-11-02-09-auction-controls

## Approach
- Controls and picks are intents on the existing single-writer runner. A pure `ControlRules` decides, then the runner persists, applies, broadcasts and only then answers. R-2/AC-2 therefore reuse the bid path, and `auction_rejections` makes repeated refusals idempotent.
- New phases and event kinds extend `Phase`, `EventKind`, `AuctionReducer` and `TimerRules`, and boot replays them (R-1, AC-1). One migration widens the `auctions.phase` check.
- Play order becomes an explicit queue of lot numbers, so re-open (R-6) and accelerated lots (R-8, R-9) add lots without renumbering the log.
- The main pool ends through main-done, where only the Commissioner chooses what happens next (R-14, R-15, AC-15).
- The web reads the real phases (R-12) and the fixture mapping goes. Screens follow the Design refs in tasks.md; 3w/3x and the main-done choice wait on a design-source step (requirements Dependencies).

## Options considered
- A (chosen): new event kinds in the one log plus a lot queue in state, with re-run lots derived in the reducer from the original card. One log and pure replay; the cost is changes to the reducer and SquadReserve.
- B: a separate controls table that the runner consults. Smaller reducer diff, but two sources of truth, and replay no longer rebuilds the phase (breaks R-2).
- C: insert re-opened and accelerated lots into `lots` and renumber. No queue needed, but lot numbers in older events, rejections and the audit log would change meaning.

## Design
Phases (`auction.model.Phase`, wire value):
- R-1 "lobby" keeps the wire value `scheduled`. The others are `live`, `paused`, `adjourned`, `resume_lobby`, `main_done`, `nominating` and `ended`.
- `AuctionState.round` is `main` or `accelerated`. Accelerated lots run in `live`.

State additions (`auction.model.AuctionState`):
- `queue`: upcoming lot numbers.
- `heldMs`: the frozen deadline or gap time in paused, adjourned and resume lobby.
- `resumeAt` and `liveAt`.
- `picks`: franchise to lot numbers.
- `round`.
- `lot()` and `nextLot()` read by lot number; `hasMoreLots()` means the queue is not empty.

Event kinds (`auction.model.EventKind`, payloads built in `Events`):
- `start_now`, `paused`, `resumed`, `lot_skipped`.
- `lot_reopened` (of, newLot, reason, franchise, amount).
- `adjourned` (resumeAt, heldMs) and `resume_time_changed` (from, to).
- `resume_lobby_opened` (liveAt), `main_done` (pool) and `nominating_opened`.
- `picks_submitted` (franchise, lots) and `round_started` (order).
- `auction_ended` gains an optional actor and intent, set when the Commissioner ends the auction.
- Controls carry the actor and the intent id.

Rules (`auction.service.ControlRules`, pure; refusal codes added to `auction.model.Reason`):
- Checks. The service checks the role; ControlRules checks phase and lot against the requirements state x entry-point matrix.
- Pause and Adjourn hold the remaining deadline (or gap) as `heldMs`. `resumed` sets the deadline to now plus `heldMs` (AC-4).
- Skip works only on a lot with no leader. It closes the lot as unsold (the lot joins the pool), then the normal gap follows (AC-6).
- Re-open, reason: required, cleaned by a new `auction.util.ReasonText` (same rules as `scoring.util.ReasonText`, which is module-private).
- Re-open, target: live phase only, including during the gap after the last lot (R-6, AC-8); the latest closed run of a player; refused while that player is queued or open.
- Re-open, effect: it reverses purse, squad and unsold entry. The new lot number is max + 1, built from the original card at base price, and goes to the front of `queue` (AC-7, AC-8).
- Adjourn works from live or paused and needs a future `resumeAt`; Change time edits it. While adjourned, the open lot, its bids and its remaining time are kept.
- Resume now, or reaching `resumeAt`, opens the resume lobby (`paddleup.auction.resume-lobby`, default 2 m). At `liveAt`, `resumed` restores the held time (AC-9).
- Change time in the resume lobby goes back to adjourned with the new time.
- Start now in either lobby goes live at once. In scheduled, `start_now` is logged before `goLive`.
- End of the main queue (R-14):
  - The between-lot gap is the existing room config `gapSeconds` (default 5 s, range 0-30), frozen in the `auction_started` payload and read as `AuctionState.gapSeconds`.
  - When the last lot's gap ends with an empty queue, `TimerRules.due` logs `main_done` if the unsold pool is non-empty, otherwise `auction_ended`.
- Main-done (R-15, AC-15):
  - The `open_round` control logs `nominating_opened`; the `end_auction` control logs `auction_ended`. Both are Commissioner only.
  - `open_round` is refused unless the phase is main_done and the round is main, so a second round is impossible.
- `picks_submitted` replaces a franchise's picks, up to its open slots (squad max minus players held; with no squad rules, the pool size) (AC-10).
- The `end_round` control closes picking and logs `round_started`. It orders picked lots by pick count (highest first), then by original lot number. Unpicked lots stay unsold, and with no picks the auction ends.
- Accelerated lots go through the existing BidGuards. An empty accelerated queue logs `auction_ended` and never `main_done` (AC-11, AC-15).
- `TimerRules.resume` (restart) leaves held phases untouched (AC-1).
- `util.SquadReserve.of` takes the set of upcoming lots instead of "lot number after", so out-of-order lots count correctly (AC-11).

REST (`AuctionController`, `AuctionService`; Commissioner check via `AuctionRoomPort.access`, 403 otherwise, AC-3, AC-15):
- `POST /api/rooms/{roomId}/auction/controls`, body `ControlRequest {intentId, action, lot?, reason?, resumeAt?}`, answers `IntentResultDto`.
- The actions are pause, resume, skip, reopen, adjourn, change_time, resume_now, start_now, open_round, end_auction and end_round.
- `PUT .../auction/picks`, body `PicksRequest {intentId, lots}`: franchise owners only, in `nominating` only.
- `GET .../auction/audit?kind=all|bids|passes|lifelines|rtm|admin&after&limit` returns `AuditDto` (AC-13).
- `auction.service.AuditViews` maps event kinds to filters. Lifeline entries keep type, lot and time only. Members only: a non-member gets 404, as on every auction route.
- `AuctionStateDto` adds `heldMs`, `resumeAt`, `liveAt`, `round`, `unsoldPool`, `pickCounts` (for every member), `myPicks`, `openSlots` and `waiting` (AC-11).

Persistence: `V11__auction_controls.sql` replaces `auctions_phase_valid` with the new phase list. No new table: picks and controls are events, and re-run lots reuse the `auction_lots` cards.

Web:
- Regenerate `web/src/api`. `web/src/auction/view.ts` maps the real phases (adds `resume_lobby`, `main_done` and `nominating`; an unknown phase is still `locked`) and drops the fixture note.
- `web/src/app/rooms/[roomId]/auction/`:
  - `notices.tsx` shows the paused and adjourned notices from state.
  - New `controls-sheet.tsx` holds the chip, sheet, skip, re-open and adjourn.
  - New `accelerated.tsx` holds the main-done choice, picking and the Commissioner view.
  - `lobby.tsx` gets Start now and the resume lobby countdown.
- `web/src/app/rooms/[roomId]/room-view.tsx` gets the commish AUCTION CONTROLS list; a new `web/src/app/rooms/[roomId]/audit/` page holds the audit screen.
- No client patch logic is needed for the new kinds: `web/src/auction/live.ts` already refetches the state on any kind it does not patch.

## Architecture changes
Patch to docs/knowledge/architecture.md "Auction engine" (2 lines added, 68 in total):
```
+ - Phases: scheduled (lobby), live, paused, adjourned, resume_lobby, main_done, nominating, ended. Commissioner controls (`POST .../auction/controls`) and accelerated picks are intents on the runner queue, logged like bids; held time, resume time and picks live only in the log.
+ - Play order is a queue of lot numbers. A re-opened or accelerated lot gets a new number, derived in the reducer from its original card, so a lot number in the log never changes meaning. After the last main lot's `gapSeconds` gap the auction goes to main_done (unsold lots left) or ended; one accelerated round, then ended.
```

## Risks
- The queue refactor could break existing engine tests. Mitigation: land it first (T-2) with the suite green before any control.
- The SquadReserve input change could shift bid limits. Mitigation: keep the current tests and add out-of-order cases.
- Ending after the last gap changes current behaviour. Mitigation: T-3 updates the end tests.
- With `gapSeconds` at 0 there is no window to re-open the last lot. Mitigation: accepted, since 0 is a Commissioner choice; T-7 tests the 0 case.
- Held time could drift on replay. Mitigation: `heldMs` is integer ms in the event, and the deadline is rebuilt from the runner's micro-truncated now().
- The re-open reason is user text shown in the audit log. Mitigation: it is sanitised, length-capped and rendered as text.
- Mock 3x draws separate Start and End buttons, but the matrix gives nominating only End round (it closes picking and runs the lots). Mitigation: T-17 reconciles the 3x copy with the matrix before any UI.
