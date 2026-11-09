---
status: final
---
# Requirements: 2026-11-02-07-auction-screen
## Problem
Remote friends bid from phones (Managers, mobile and desktop). A slow, jumpy or silent screen loses lots. The merged auction engine (2026-11-02-06-auction-engine) has no UI yet. Covers FR-40 (lobby), FR-41, FR-110, FR-111 (reconnecting state only), FR-112. Module: web. Direction chosen: A Spotlight.
## Requirements
- R-1: Lobby (FR-40): shows the scheduled start time in the viewer's local zone, a countdown to it, and one readiness row per franchise (READY / IN LOBBY / NOT HERE) per Room.dc.html `lobby`.
- R-2: A manager can mark themselves ready and undo it before start (intent only; the server's readiness renders). IN LOBBY is live-connection presence.
- R-3: When the server starts the auction, the lobby moves to the auction screen without a manual reload.
- R-4: Lot card (FR-41): player name, role, tags, base price, last-season points if present, optional photo, set name; plus live bid state (current bid, leader, my status).
- R-5: Role, tag, stat and currency labels and units come from the room's Sport Pack; no sport string in core UI.
- R-6: Countdown shows server-timed time left (server deadline plus offset measured at sync); no client clock is trusted.
- R-7: One-tap "bid next increment" (FR-42 intent) is the primary action; the next amount is server-provided; the outcome (accepted, outbid, rejected with the guard's reason) shows from the server reply only.
- R-8: Current bid and my purse are always visible, DM Mono tabular; no layout shift when bids, purse or timer change (FR-112).
- R-9: Bid dock sits in the thumb zone on phone (72px button); a reserved slot beside it holds the lifeline entry point so it never displaces the bid button (FR-112).
- R-10: Offline (FR-111, FR-50), per 3f: the "Reconnecting…" notice ("Bidding's locked until you're back. Nothing is lost.") with a Retry button, the bid button locked and labelled "Reconnecting…", and the timer frozen at the last server value, greyed, noted "WAITING". On reconnect all local state is replaced by a full server resync.
- R-11: Render server-driven states: open, bidding, sold, unsold, paused, adjourned, outbid, rejected; read-only when the viewer has no franchise.
- R-12: Responsive at 390, 820 and 1440 (FR-110), light and dark, reduced motion = fades only; the look follows Auction A Spotlight.dc.html.
- R-13: Schedule (FR-40, engine 06 R-1): the Commissioner sets the start from the room (setup or retention) and changes it from the lobby until go-live, picking a date and time in their own zone that is sent as one UTC instant. The room moves only on the server's reply; a past time or a refusal (no or too small a pool) shows the server's reason. A room already scheduled with no start offers the same sheet. Managers get no schedule control.
Design refs: docs/design/README.md; Auction A Spotlight.dc.html (with AuctionBidDock, AuctionNotices), scenes 3a outbid, 3b rejected, 3d paused, 3e adjourned, 3f reconnecting, 3g sold, 3h unsold, 3j dark (in Auction Explorations.dc.html); Room.dc.html `lobby`, `sheetSchedule`, `sheetScheduleError`, `sheetScheduleRefused`, `lobbyReschedule` (App Screens 6o1-6o3, 3b4); Design System.dc.html.
## State x entry-point matrix
Entry points: N = first load or new tab, R = reload, D = socket drop then reconnect, S = stale tab (slept or backgrounded, then foregrounded). Every cell ends in server truth; none reads local state.
| State | N / R | D | S |
|---|---|---|---|
| Scheduled (lobby) | lobby with countdown | Reconnecting notice, resync, countdown re-derived | resync on focus, countdown corrected |
| Started: open, bidding, sold, unsold | current lot at server bid and time left | bid locked, timer frozen (R-10), resync lands on the current lot | resync on focus before any bid is accepted |
| Paused / adjourned | read-only notice, bid locked | same notice after resync | same notice after resync |
| Closed / not mine | no auction UI; link to room (summary out of scope) | same | same |
| Not scheduled (setup, retention, or scheduled with no start) | Commissioner: Schedule auction opens the sheet; others: no auction UI, room says the Commissioner sets the time | same | same |

## Acceptance criteria
- AC-1: With a scheduled start, the lobby shows the local start time, a countdown and a status chip per franchise; the countdown stays within 1s of the server when the browser clock is skewed by 5 minutes.
- AC-2: Tapping ready flips only my row after the server confirms; a second tap undoes it; another tab shows the change within 2s.
- AC-3: When the server starts the auction, an open lobby tab shows the lot screen within 2s, with no reload.
- AC-4: The lot card shows every field of R-4 present in the data; a missing photo or last-season points leaves no empty gap and no layout change.
- AC-5: With the stub football pack, no cricket role, tag, stat or currency string renders on lobby or lot screens.
- AC-6: Lot countdown matches the server deadline within 1s with the browser clock skewed by 5 minutes; it resets within 500ms of a bid broadcast.
- AC-7: One tap on the bid button sends exactly one bid intent; a double-tap within 300ms sends one.
- AC-8: A bid rejected by the eligibility guard shows the server's reason and leaves the displayed bid, purse and leader unchanged.
- AC-9: When another franchise bids, my screen shows outbid within 500ms of the broadcast and the bid button shows the new next amount.
- AC-10: Over 20 consecutive bid broadcasts, the bounding boxes of the bid button, purse, current-bid and timer containers do not change (Playwright, 390 and 1440).
- AC-11: At 390x844 the bid button is at least 72px tall, inside the bottom third, and the lifeline slot is a distinct element that does not overlap it.
- AC-12: Current bid and my purse are visible without scrolling at 390x844 and 1440x900, in every state of R-11.
- AC-13: Dropping the network shows the R-10 notice within 3s, locks the bid button and freezes the timer; restoring it re-enables bidding only after a full resync is applied.
- AC-14: After a drop during which a lot was sold, the screen shows the current lot and my purse equals the server's.
- AC-15: Reload, new tab and foregrounding a stale tab each show the current server state within 2s; the stale tab sends no bid before resync completes.
- AC-16: Paused and adjourned show the read-only notice and a locked bid button; sold and unsold show the result matching the server event.
- AC-17: A viewer with no franchise sees the lot and live bids but no bid button; the cause is stated.
- AC-18: Screenshots at 390 and 1440 of every changed screen sit beside their mock with no visible token, spacing or type deviation; reduced motion removes number-roll and stamp animation.
- AC-19: The Commissioner of a setup or retention room picks a future local date and time and the client sends one schedule call with the matching UTC instant; on success the room shows the lobby with that time and countdown, and a manager in another zone sees it in theirs. Change time in the lobby moves every open lobby's countdown within 2s, no reload, ready taps kept.
- AC-20: A past time shows the server's field error; a pool refusal shows the server's reason; neither changes the room. A manager sees no Schedule or Change time control, and the server answers their schedule call 403.

## Prerequisites
- P-1: Before build, draw in Room.dc.html / Auction A Spotlight.dc.html and get approval for: lobby past start time, lobby offline, Commissioner with no franchise (lobby and lot), entering a closed or adjourned auction. R-1, R-10, R-11, AC-17 and the Closed row are not buildable until approved. Added 2026-11-09: the schedule sheet and lobby Change time (R-13); R-13 is not buildable until approved.
- P-2: Ready intent and presence (R-2) may need a server change if engine 06 lacks them; confirm at plan.

## Out of scope
- Team Table advisor, lifeline behaviour, RTM flow (FR-49), pass, proxy bid, Commissioner controls (FR-47; scheduling is in via R-13), audit log, summary screen; feed, rivals and squad panels (later specs); offline cache, service worker and manifest (rest of FR-111); any engine or schema change beyond what the engine already exposes (see P-2).
