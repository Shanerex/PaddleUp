---
status: final
---
# Tasks: 2026-11-02-09-auction-controls

## Tasks
Design refs for every UI task: docs/design/README.md plus the screens named in the task; 3n-3y are Auction Explorations scenes drawn in Auction A Spotlight.dc.html.
- [x] T-1 (AC-1): V11__auction_controls.sql widens auctions_phase_valid; Phase gains PAUSED, ADJOURNED, RESUME_LOBBY, MAIN_DONE, NOMINATING; IntentRules rejects bids and passes in every non-live phase; migration and rules tests.
- [x] T-2 (AC-8, AC-11): AuctionState play queue of lot nos (lot, nextLot, hasMoreLots read it); SquadReserve takes the upcoming lot set; existing engine and guard suites stay green; add out-of-order reserve cases.
- [ ] T-3 (AC-8, AC-15): after the last main lot the gapSeconds gap runs, then main_done if unsold lots exist, else auction_ended; existing end-of-auction tests updated.
- [ ] T-4 (AC-2, AC-3): ControlRequest, ControlRules skeleton, POST .../auction/controls; non-Commissioner gets 403, no event, no broadcast; one intent id replayed adds one event; refusals stored in auction_rejections.
- [ ] T-5 (AC-4): pause and resume: heldMs from the deadline or gap; no expiry while paused; bids and passes rejected; resumed restores the exact remaining time.
- [ ] T-6 (AC-6): skip on a lot with no bid closes it as unsold and logs lot_skipped; with a bid it is refused with a reason and writes no event.
- [ ] T-7 (AC-7, AC-8): auction.util.ReasonText; re-open: no reason rejected; purse, squad and unsold reversed; new lot at the front of the queue at base price with no bids; last lot re-openable in its gap (phase changes only after; gapSeconds 0 case); reason in the event.
- [ ] T-8 (AC-9): adjourn with a future resumeAt keeping the lot, bids and remaining time; Change time, Resume now, 2-minute resume lobby (paddleup.auction.resume-lobby) and scheduled resume in TimerRules; never auto-live from adjourned.
- [ ] T-9 (AC-2, AC-14): Start now in the lobby and the resume lobby: start_now logged before go-live; a refused go-live still logs start_refused.
- [ ] T-10 (AC-15): main_done: open_round opens nominating, end_auction ends; a Manager sending either gets 403 and no event; open_round outside main_done or after the accelerated round is rejected; empty accelerated queue ends.
- [ ] T-11 (AC-10): unsold pool holds no-bid lots and never sold ones; PUT .../auction/picks within open slots accepted, beyond rejected, nominating only.
- [ ] T-12 (AC-11): unsoldPool, pickCounts, myPicks, openSlots and waiting in AuctionStateDto for every member; end_round runs picked lots most-picked first (tie: original lot no).
- [ ] T-13 (AC-11): a won accelerated lot still fails the eligibility guard when FR-44 is breached.
- [ ] T-14 (AC-1, AC-9): restart test in scheduled, live, paused, adjourned, resume lobby, main_done and nominating: phase, lot, deadline or heldMs and picks equal; adjourned stays locked.
- [ ] T-15 (AC-2): for every control kind the event is stored before broadcast; a forced log-write failure broadcasts nothing and leaves state unchanged.
- [ ] T-16 (AC-13): GET .../auction/audit: filters, time order, lifeline entries hold type, lot and time only, non-member 404, empty page; OpenAPI regenerated into web/src/api.
- [ ] T-17 (AC-11, AC-14, AC-15): BLOCKING, design source only, no code: edit 3w/3x (open-slots wording, pick counts shown to all, 3x buttons reconciled with the matrix's End round) and draw the main-done Commissioner choice; get the human's approval; T-27, T-28 and T-29 wait on it.
- [ ] T-18 (AC-14): web/src/auction/view.ts reads the real phases and drops the fixture mapping; resume_lobby, main_done and nominating handled; unknown phases still lock; view tests.
- [ ] T-19 (AC-4, AC-14): notices.tsx paused and adjourned notices from state (heldMs, resumeAt), resume lobby countdown. Design refs: AuctionNotices paused notice and replay toast; Auction A paused, adjourned.
- [ ] T-20 (AC-14): lobby Start now for the Commissioner (audited). Design refs: Room lobby, lobbyReschedule.
- [ ] T-21 (AC-4, AC-14): Controls chip (Commissioner only) and sheet with Pause and Resume. Design refs: Explorations 3n, 3o.
- [ ] T-22 (AC-6, AC-14): skip confirm, skip refused and lot skipped states. Design refs: Explorations 3p, 3q, 3t; Auction A unsold.
- [ ] T-23 (AC-7, AC-14): re-open with required reason, and the re-opened lot as managers see it. Design refs: Explorations 3r, 3s.
- [ ] T-24 (AC-9, AC-14): adjourn time picker, adjourned Commissioner view (Resume now, Change time), auction-screen adjourned button. Design refs: Explorations 3u, 3v; Auction A adjourned.
- [ ] T-25 (AC-14): Room commish AUCTION CONTROLS list. Design refs: Room commish; App Screens 9a commishLive.
- [ ] T-26 (AC-13): audit screen, filters All/Bids/Passes/Lifelines/RTM/Admin (Scoring as drawn, empty), empty state. Design refs: Room audit; App Screens 9b audit, 9c auditEmpty.
- [ ] T-27 (AC-14, AC-15): end-of-main choice: the Commissioner opens the accelerated round or ends the auction; managers see main-done read-only. Design refs: the main-done state drawn in T-17.
- [ ] T-28 (AC-10, AC-11): accelerated round, a manager picks from the unsold pool up to open slots, pick counts shown. Design refs: Explorations 3w as edited in T-17.
- [ ] T-29 (AC-11): accelerated round Commissioner view (pick counts, End round) and accelerated lot live. Design refs: Explorations 3x as edited in T-17, 3y.
- [ ] T-30 (AC-14): Playwright at 390 and 1440 beside the mocks: Start now, controls, adjourned button, end-of-main choice, paused and adjourned notices from real phases, new tab and stale state; stub pack and check-agnostic pass unchanged.
- [ ] T-31 (AC-2, AC-8): decision entries in docs/knowledge/decisions: controls as runner intents in the one log; play queue with derived re-run lots.
