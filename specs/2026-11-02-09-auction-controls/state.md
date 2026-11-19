---
feature: 2026-11-02-09-auction-controls
phase: verify
status: draft
g1: approved:Shanerex:2026-11-17
g2: approved:Shanerex:2026-11-17
g3: none
bump: none
next: qa and security re-verify only (fix loop 3 done), then g3 and bump
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
- 2026-11-17 build: T-30 done: side-by-side at 390 and 1440 reviewed; auction Commissioner sheets now draw the mock title row with a close button (Sheet title prop, adjourn via ScheduleSheet titleRow); adjourned before any lot says so instead of Lot 0 (copy.ts adjournedLine); e2e 10/10, make test and make lint green
- 2026-11-17 build: all 32 tasks ticked; side-by-side at 390/1440 reviewed by orchestrator (sheet title row and zero-lots adjourn copy fixed in 0952247); phase verify
- 2026-11-17 verify: qa pass with notes, security pass (S-1, S-2 minor), /code-review 10 findings; human: picks private, CR-9/CR-10 in scope; review.md verdict fail, fix loop 1
- 2026-11-18 fix-loop-1: CR-1, CR-2, CR-3/S-2 (+F-4), CR-4..CR-10 and F-2 fixed test first, one commit per fix; picks private (no lot list to others over WS, /events, /audit; admin audit category; stored event intact); reason cap 140 code points; mock pairs at 390 and 1440 in e2e/pairs.spec.ts (auction sheets now a right panel at desktop, role chip and buyer monogram on the skip and re-open rows); not drawn in the mock, deferred: resume lobby (paired with 3e), time of sale on the re-open row (state has no sale time), adjourn copy "after Lot N" (spec copy kept)
- 2026-11-18 verify: fix loop 1 done (CR-1..CR-10, S-1, S-2, F-2, F-4; 11 commits); pairs regenerated and spot-checked by orchestrator; re-verify
- 2026-11-18 verify: round 2 qa pass, security pass (S-3 minor), /code-review 10 findings; human: S-3 accepted (picks semi-public), loop 2 bugs only; fix loop 2
- 2026-11-18 verify: deferred to a new spec (not yet on roadmap): CR2-7 audit server-side kind filter and paging, CR2-8 shared sanitiser with scoring, CR2-9 dead ROUND_DONE branch
- 2026-11-18 fix-loop-2: CR2-2..CR2-6 and CR2-10 fixed test first, 4 commits: Start now and Change time in the resume lobby (adjourned card layout, 3v pair), next-lot labels use nextLot in a gap (re-open sheet says "before Lot N" there, since a re-opened lot goes to the queue head), state DTO sends lotsClosed (reducer count, re-open takes one off) and resumeLobbySeconds (TimerRules.RESUME_LOBBY and 2-arg due removed), late bid on a closed lot answers lot_closed in every phase with client copy for paused/adjourned/resume_lobby/main_done/nominating, DM Mono on the two counts; make test and make lint green, e2e pairs regenerated; CR2-1 accepted, CR2-7..9 deferred
- 2026-11-18 verify: fix loop 2 done; round 3 qa pass, security pass, /code-review 10 new findings (CR3-1..CR3-6 bugs); blocked after 2 loops
- 2026-11-18 verify: human approved fix loop 3 for CR3-1..CR3-6, re-verify with qa + security only; deferred to a new spec (not yet on roadmap): CR3-7 zero/changed picks, CR3-8 held-phase tick, CR3-9 shared sanitiser, CR3-10 Phase.timed()/held()
- 2026-11-19 fix-loop-3: CR3-1..CR3-6 fixed test first, one commit each; Start now the room cannot honour is refused not_ready with no event, a go-live write failure after start_now stays accepted and the tick retries, a gap pause logs no lot (payload gap) and reads "Paused between lots", re-open notice uses newLot, adjourned card says "after N lots", the skip and re-open notice has its own slot; unit and web tests, spotless, agnostic check green; ITs and make test NOT run: Docker image store broken (containerd meta.db input/output error, postgres:17 cannot be pulled); CR3-7..CR3-10 untouched (deferred)
