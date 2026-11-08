---
status: final
---
# Requirements: 2026-11-02-06-auction-engine
## Problem
Managers need a live, fair, remote auction the server fully controls (FR-40..43, FR-45, FR-48, FR-50); it is the riskiest component (PRD 8), built first on a small fake pool, with an append-only log that survives restarts.

## State x entry-point matrix
Lifetime: one auction. The pack is frozen from scheduling (FR-15); pool, set order and config freeze at go-live.
| entry point | none | lobby (scheduled) | lot open | gap (between lots) | ended |
|---|---|---|---|---|---|
| Commissioner schedule | creates (R-1) | replaces time, logged (R-1) | 409 | 409 | 409 |
| Start instant reached | n/a | goes live, lot 1 opens; if refused stays scheduled, start_refused logged (R-2) | n/a | n/a | n/a |
| Bid intent | 409 not_live | 409 not_live | first = base price, then +step; wrong amount or lost race = outbid (R-4,R-9) | naming the lot just closed: `lot_closed`; a later lot: 409 no_open_lot | naming a lot that closed: `lot_closed`; a lot past the last: 409 ended |
| Pass intent | 409 | 409 | logged, bars that lot only (R-6) | as bid | as bid |
| Deadline reached | n/a | n/a | sold or unsold, then gap (R-8) | next lot, or ended after the last (R-8) | n/a |
| Server restart | n/a | kept; start passed in downtime goes live or is refused (R-14) | rebuilt from log; past deadline = resume event + fresh T_bid (R-14) | continues (R-14) | stays ended |
| Member connects, reconnects, new tab | 404 | lobby state, incl. start_refused reason (R-13) | full state + deadline (R-13) | last result + next lot (R-13) | final state (R-13) |
| Pool, set-order, config write (05) | allowed | allowed until go-live (R-1) | 409 (R-2) | 409 | 409 |

## Requirements
- R-1: Commissioner schedules, and before go-live reschedules, a future UTC start; refused without a pool copy or a big enough pool (05 R-5, R-15). Members see a lobby with a server-time countdown and each franchise's connected status (FR-40). Pool, set-order and config stay editable until go-live (05 R-8).
- R-2: At the start instant on the server clock the auction goes live with no request; its first live-phase event snapshots ladder, T_open, T_bid, G, purses and lot order, then lot 1 opens. If go-live is refused (pool below the 05 R-15 minimum, or too few franchises) the room stays scheduled, one `start_refused` event with the reason is logged and shown in the lobby, and the Commissioner fixes it and reschedules. From go-live, pool, set-order and config writes return 409 (05 R-8).
- R-3: Lots follow the room set order (05 R-9), players within a set in dataset order; a lot broadcast carries player card fields, base price, set, position and live bid state (FR-41).
- R-4: The first bid equals the base price; each next bid equals the current bid plus the increment of the ladder band holding it (room config, pack default; FR-42). A bid names lot and amount; any other amount is rejected `outbid`.
- R-5: A bid is rejected, with a reason and no event, if the bidder is not a room franchise, already leads the lot, has passed it, or lacks purse for the amount. The FR-44 composition guard plugs in at this point; this spec ships purse only and 2026-11-02-08-eligibility-guard owns FR-44.
- R-6: A pass intent is logged for franchise and lot and bars that franchise from that lot (one player) only; it neither moves the timer nor closes the lot.
- R-7: A lot opens with T_open; each accepted bid sets the deadline to server now + T_bid. T_open and T_bid come from room config, default 20 s and 10 s, with no pack default. Deadlines are absolute UTC instants in every broadcast; no client time is read (FR-43, NFR-1). An accepted bid reaches subscribers within 500 ms (NFR-2).
- R-8: At the deadline the lot closes: the highest bidder wins at the bid price (purse debited, player added to its squad); no bids means unsold. A bid handled before close counts, after it gets `lot_closed`. After a gap G (room config, default 5 s, no pack default) the next lot opens; after the last lot the auction is ended and unsold players stay listed.
- R-9: Intents for one auction are applied one at a time in server arrival order; of two bids for the same amount the first is accepted and the other gets `outbid` at once (FR-45).
- R-10: Each intent carries a client intent id; a repeat returns the first outcome and writes nothing new. Bids are irrevocable: no cancel or undo intent exists.
- R-11: Every state change is an event in an append-only per-auction log (gap-free seq, server UTC time, kind, franchise, lot, payload) committed before any broadcast or reply; no update or delete. Lifeline kinds, when added, carry type, lot and time only (FR-48, NFR-4, NFR-8).
- R-12: Members read the log over REST from a given seq; non-members get 404, so the room's existence is not revealed (FR-48).
- R-13: A connecting or reconnecting member gets full state (phase, lot, bid, leader, deadline, purses, squads so far, last seq, any start_refused reason); events carry seq so a gap triggers a resync; a dropped franchise just does not bid (FR-50).
- R-14: On boot every unended auction is rebuilt from its log and continues; a start passed during downtime goes live or is refused per R-2; an open lot whose deadline passed in downtime gets a logged resume event and a fresh T_bid.
- R-15: Only room members can connect, subscribe or send intents; bid and pass are REST POSTs, and STOMP carries auction broadcasts only.
- R-16: No core code, schema or message names a sport, role, stat or currency; the stub football pack runs a scripted auction with zero core changes (NFR-12).

## Acceptance criteria
- AC-1: A future start is accepted and members see a lobby countdown from server time; a past start returns 400, a member 403, and no pool copy or too small a pool is refused with the room unscheduled (R-1).
- AC-2: While scheduled, pool, set-order and config writes succeed and rescheduling works; after go-live the writes and a reschedule return 409 and change nothing (R-1,R-2).
- AC-3: With a test clock at the start instant, the auction goes live with no request; the first event of the live phase (scheduled and rescheduled events come before it) holds the snapshot, and lot 1 is the first player of the first set, broadcast with card fields and deadline = open + T_open (R-2,R-3,R-7).
- AC-4: On a two-band ladder, bids at base, below, at and above a band edge are accepted only at base then current + band step; any other amount gets `outbid` (R-4).
- AC-5: A bid by the leader, a non-member, a franchise with purse below the amount, or after its own pass on that lot is rejected with a reason and writes no event (R-5,R-6).
- AC-6: A pass appears in the log; the passing franchise's later bid on that lot is rejected but its bid on the next lot is accepted; deadline and lot state are unchanged by the pass (R-6).
- AC-7: An accepted bid moves the deadline to server now + T_bid; a client with a clock off by an hour sees the same deadline and the same outcomes (R-7).
- AC-8: At the deadline the leader wins, purse is debited and the player is in its squad; with no bids the player is unsold; a bid after the deadline gets `lot_closed`; the next lot opens after G and the last close ends the auction with unsold listed (R-8).
- AC-9: Two bids for the same amount sent concurrently, repeated 20 times: exactly one accepted, the other gets `outbid`, the log holds one bid at that amount (R-9).
- AC-10: Resending an intent id returns the first outcome with one event; no cancel or undo intent is accepted (R-10).
- AC-11: With the log write forced to fail, the bid returns an error, nothing is broadcast and state is unchanged; log seq is gap-free; SQL update or delete on the log is rejected (R-11).
- AC-12: A member reads the log from seq N and gets events N+1 onward; a non-member gets 404 (R-12,R-15).
- AC-13: A reconnecting member and a new tab get state equal to the server's; a client that misses an event detects the seq gap and resyncs to equal state (R-13).
- AC-14: Killing and restarting the server mid-lot with bids restores the same lot, price, leader, purses and seq with no event lost or duplicated; a deadline passed in downtime logs a resume event and the lot gets a fresh T_bid; lobby and gap states resume too (R-14).
- AC-15: With 10 subscribers, bid-to-broadcast is under 500 ms at p95 over 100 bids in the local test (R-7).
- AC-16: The stub football pack and a 20-player fake pool run a scripted auction (sold and unsold lots) to ended with zero core changes (R-16).
- AC-17: At the start instant with the pool below the 05 R-15 minimum (or too few franchises), no lot opens, the room stays scheduled, exactly one `start_refused` event with the reason is logged and shown in the lobby; after the Commissioner fixes it and reschedules, the auction goes live (R-2,R-13).

## Out of scope
- Auction screen (07), Commissioner controls: pause, skip, re-open, adjourn, cancel schedule (09), summary (19), accelerated round (FR-46), RTM (FR-49), proxy bids (FR-51), lifelines and advisor.
- Full FR-44 composition guard (spec 2026-11-02-08-eligibility-guard); rate limiting (NFR-7).
