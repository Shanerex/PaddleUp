---
status: final
---
# Requirements: 2026-11-02-09-auction-controls

## Problem
Commissioners of real, multi-evening auctions cannot pause, skip, correct a wrongly closed lot, adjourn, or re-run unsold players, and managers have no dispute trail (FR-46, FR-47, FR-48).
Commissioner (controls) and all Managers (read the audit log). Also closes deferred work: adjourned button, lobby Start now, real paused/adjourned phases (3d/3e).
Design refs: Auction Explorations 3n-3y; Auction A paused, adjourned, unsold; App Screens 9a-9c; Room commish, audit, lobby, lobbyReschedule; AuctionNotices.

## Requirements
- R-1: The engine has explicit phases: lobby, live, paused, adjourned, resume lobby, main-done, accelerated nominating, ended; unknown phases still lock bidding.
- R-2: Every control and its outcome is appended to the event log before broadcast; restart rebuilds phase from the log; repeated intents are idempotent.
- R-3: Only the Commissioner may send control intents; the server checks role and phase, trusts no client state.
- R-4: Pause freezes the bid timer and rejects bids and passes; Resume restores the remaining time; the advisor never pauses or extends the timer (FR-43).
- R-5: Skip lot is allowed only while the lot has no bid; otherwise refused with a stated reason and no change.
- R-6: Re-open a closed lot requires a reason (sanitised, length-capped); the sale is reversed; the lot returns right after the current lot, restarting from base price; audited; the last lot of the main pool can be re-opened until its gap ends (R-14).
- R-7: Adjourn stores a resume time, locks bidding and survives restart; Resume now and the scheduled resume time both open a 2-minute resume lobby before the lot goes live; Change time edits the stored time.
- R-8: Lots closed with no bid join the unsold pool; in the accelerated round each franchise nominates up to its open squad slots (squad maximum minus players held), no other cap.
- R-9: Pick counts are visible to all managers; accelerated lots run most-picked first, then as normal lots through the eligibility guard (FR-42..45).
- R-11: Audit log lists every bid, pass, RTM, lifeline (type, lot, time only) and admin action with UTC time, read-only for every room member, filters All/Bids/Passes/Lifelines/RTM/Admin, with an empty state.
- R-12: UI follows the Design refs: Controls chip and sheet, adjourned button, lobby Start now (Commissioner, audited), end-of-main choice, Room commish list, audit screen; paused and adjourned notices read the real phases and replace the fixture mapping from 2026-11-02-07-auction-screen.
- R-13: No core code, schema or string names a sport, role or currency; the stub pack passes with zero core changes.
- R-14: After the main pool's last lot closes, the engine waits the normal between-lot gap, then enters main-done if unsold lots exist, else ended.
- R-15: In main-done only the Commissioner may open the accelerated round (to accelerated nominating) or end the auction (to ended); one accelerated round only, after which the auction ends.

## State x entry-point matrix
Cells name the outcome; "reject" returns a reason, writes no event. Lifetime states only.
| State | Commissioner control | Manager bid/pass | Timer or schedule | Restart / new tab |
|---|---|---|---|---|
| Lobby / resume lobby | Start now, Change time | reject | start time opens first lot; resume lobby lasts 2 min, then live | stays; lobby resynced |
| Live | Pause, Skip (no bids only), Re-open, Adjourn | accepted (FR-42..45) | expiry: sold or unsold; after the last lot, gap then main-done or ended | rebuilt from log, same lot and time |
| Paused | Resume, Adjourn; Skip, Re-open reject | reject | frozen, no expiry | stays paused |
| Adjourned | Resume now, Change time; others reject | reject | resume time opens resume lobby, never auto-live | stays adjourned, notice shown |
| Main-done | Open accelerated round, End auction; others reject | reject | none | stays main-done; Commissioner choice shown |
| Accelerated nominating | End round | nominate only, up to open slots | none | picks kept; counts visible to all |
| Ended | all reject | reject | none | link to room |

## Acceptance criteria
- AC-1 (R-1, R-2): After a server restart in lobby, live, paused, adjourned, main-done and nominating, phase, lot and remaining time equal the pre-restart values.
- AC-2 (R-2): Each control event is persisted before its broadcast; a failed write broadcasts nothing; replaying one intent id adds one event.
- AC-3 (R-3): A non-Commissioner control intent returns 403, writes no event, broadcasts nothing.
- AC-4 (R-4): While paused, bids and passes are rejected and the timer does not advance; after Resume the remaining time equals the value at pause.
- AC-6 (R-5): Skip on a lot with no bid closes it and is audited; skip on a lot with a bid is refused with a reason and writes no event.
- AC-7 (R-6): Re-open without a reason is rejected; with one, purse and squad return to pre-sale values and the audit entry holds the reason.
- AC-8 (R-6, R-14): The re-opened lot is the next lot after the current one and opens at base price with no earlier bids; a wrongly closed last lot can be re-opened during its gap, and the phase changes (R-14) only after the gap.
- AC-9 (R-7): Adjourn then restart keeps bidding locked; Resume now opens a 2-minute resume lobby, then the lot goes live; Change time updates the stored time.
- AC-10 (R-8): A no-bid lot appears in the unsold pool; a sold lot does not; a nomination beyond a franchise's open squad slots is rejected, one within them is accepted.
- AC-11 (R-9): Every room member's response includes pick counts; accelerated lots run most-picked first; a won accelerated lot still fails the eligibility guard when FR-44 is breached.
- AC-13 (R-11): A room member sees bid, pass, RTM, lifeline and admin entries in time order (non-member gets 404); lifeline entries carry only type, lot and time; each filter returns only its kind; an empty log shows the empty state.
- AC-14 (R-12, R-13): Playwright at 390 and 1440 against the mocks: Start now, controls, adjourned button, end-of-main choice, paused and adjourned notices from real phases (new tab and stale state included); stub pack passes unchanged.
- AC-15 (R-14, R-15): With unsold lots the main pool ends in main-done, with none in ended; the Commissioner can open the accelerated round or end the auction; a Manager sending either returns 403 and writes no event; a second accelerated round is rejected.

## Out of scope
- Scoring-sourced audit entries (the Scoring filter stays empty until the scoring spec); RTM flow (17); push notices (21).
- Deferred to 2026-11-02-26-advisor-lifelines: bid timer unchanged by lifeline requests in every phase (was AC-5); advisor prompts exclude pick counts, accelerated order, unsold pool and upcoming lots (was R-10/AC-12).

## Dependencies
- Design-source follow-up before the UI tasks: Explorations 3n-3y say "up to 3 picks" and show pick counts to the Commissioner only; they need a small source edit (open-slots wording, counts shown to all) plus approval. No layout is guessed here.
- The end-of-main Commissioner choice (open accelerated round / end auction) needs a drawn state in the design source, done with the 3n-3y edits and approved before UI tasks.
