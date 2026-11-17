---
feature: 2026-11-02-09-auction-controls
phase: build
status: blocked
g1: approved:Shanerex:2026-11-17
g2: approved:Shanerex:2026-11-17
g3: none
bump: none
next: human runs the mock server on 8099 for the T-30 side-by-side (commands in the T-30 log line), then dev ticks T-30
---
# State: 2026-11-02-09-auction-controls

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: FR-46, FR-47, FR-48; module auction; depends on 2026-11-02-08-eligibility-guard; who: Commissioner (controls), all Managers (audit); deferred in from 07 (adjourned button, paused/adjourned notices) and 08 (lobby Start now); design refs P-1 drawn 2026-11-17 (3n-3y, App Screens 9a-9c)
- 2026-11-17 requirements/plan: pm and architect two passes; g1 and g2 approved by Shanerex; End round = close picking and play picked lots most-picked first (none: auction ends); AC-5/AC-12 deferred to 2026-11-02-26-advisor-lifelines
- 2026-11-17 build: T-17 design edits drafted, NOT approved: Auction A Spotlight.dc.html + paddle.js scenes accelNominate/accelCommish (3w, 3x) edited, new scenes mainDoneCommish/mainDone (3z, 3aa) drawn; Explorations + README rows updated; not viewed in a browser
- 2026-11-17 build: T-30 partly done (e2e/controls.spec.ts passes at 390 and 1440 for Start now, pause, skip, re-open, adjourn, resume lobby); end-of-main and the beside-the-mock comparison still to do
- 2026-11-17 build: deferred to 2026-11-02-17-retention-rtm: RTM audit entries (no rtm event kinds exist yet; the RTM filter returns none)
- 2026-11-17 build: deferred to 2026-11-02-26-advisor-lifelines: lifeline_spent/refunded kinds are reserved and audited as type, lot and time only (no franchise shown, unlike the audit mock)
- 2026-11-17 build: mock text differs from spec: adjourn sheet says bidding stops after the lot closes; the engine adjourns at once and keeps the lot, bids and time (plan Q-3); sheet copy follows the spec
- 2026-11-17 build: T-1..T-16, T-18..T-26, T-31 done; T-17 design edits (3w, 3x, 3z, 3aa; commit 72427ec) approved by Shanerex
- 2026-11-17 build: T-27, T-28, T-29 done (web accelerated.tsx: main-done choice, picking, Commissioner End round, accelerated lot header); make test and make lint green
- 2026-11-17 build: T-30 e2e added and passing at 390 and 1440 (end of main, picks, End round, accelerated lots, End the auction, no-picks end, new tab, reload, manager 403); NOT ticked: beside-the-mock comparison, python3 -m http.server 8099 in docs/design/source was denied again, no workaround tried; pairing code is in e2e/support/mocks.ts (E2E_MOCKS opt-in)
- 2026-11-17 build: mock differs from 3y: accelerated lot shows "Accelerated round · Lot N" with no "n of m" and no "Picked by N" tag; the state DTO has neither (pickCounts is empty once the round starts, the queue size is not sent); deferred: add the fields in a follow-up if the human wants them
- 2026-11-17 build: mock differs from 3w/3x: the pool lists every unsold player (no "Showing 8 of N" line, no search or role filter, which the mock only names); role chip shows the pack label, not a short code; a Commissioner without a franchise (3x draws one with a franchise) sees a neutral intro and two stat cells, no picking
- 2026-11-17 build: human chose to fix the 3y gap now: T-32 added (accelerated queue position/size and pick count in the state DTO, header "n of m" and "Picked by N")
- 2026-11-17 build: deferred to a new spec (not yet on roadmap): 3w/3x pool "Showing 8 of N" cap, search and role filter; human approved the deferral
- 2026-11-17 build: T-32 done (reducer accel slots, state DTO accelPosition/accelSize/lotPicks, header "n of m" and Picked by tag; e2e asserts both at 390 and 1440; make test and make lint green); T-30 still not ticked
