---
feature: 2026-11-02-09-auction-controls
phase: build
status: blocked
g1: approved:Shanerex:2026-11-17
g2: approved:Shanerex:2026-11-17
g3: none
bump: none
next: human approves T-17 design edits (Auction A Spotlight.dc.html and paddle.js scenes accelNominate, accelCommish, mainDoneCommish, mainDone; Explorations 3w, 3x, 3z, 3aa), then /rx:next
---
# State: 2026-11-02-09-auction-controls

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: FR-46, FR-47, FR-48; module auction; depends on 2026-11-02-08-eligibility-guard; who: Commissioner (controls), all Managers (audit); deferred in from 07 (adjourned button, paused/adjourned notices) and 08 (lobby Start now); design refs P-1 drawn 2026-11-17 (3n-3y, App Screens 9a-9c)
- 2026-11-17 requirements/plan: pm and architect two passes; g1 and g2 approved by Shanerex; End round = close picking and play picked lots most-picked first (none: auction ends); AC-5/AC-12 deferred to 2026-11-02-26-advisor-lifelines
- 2026-11-17 build: T-1..T-16 done (server: phases, queue, controls, accelerated round, audit; make test green)
- 2026-11-17 build: T-17 design edits drafted, NOT approved: Auction A Spotlight.dc.html + paddle.js scenes accelNominate/accelCommish (3w, 3x) edited, new scenes mainDoneCommish/mainDone (3z, 3aa) drawn; Explorations + README rows updated; not viewed in a browser
- 2026-11-17 build: T-18..T-26 and T-31 done (web: real phases, notices, Start now, Controls sheet, Room controls card, audit screen)
- 2026-11-17 build: T-30 partly done (e2e/controls.spec.ts passes at 390 and 1440 for Start now, pause, skip, re-open, adjourn, resume lobby); end-of-main and the beside-the-mock comparison still to do
- 2026-11-17 build: deferred to 2026-11-02-17-retention-rtm: RTM audit entries (no rtm event kinds exist yet; the RTM filter returns none)
- 2026-11-17 build: deferred to 2026-11-02-26-advisor-lifelines: lifeline_spent/refunded kinds are reserved and audited as type, lot and time only (no franchise shown, unlike the audit mock)
- 2026-11-17 build: mock text differs from spec: adjourn sheet says bidding stops after the lot closes; the engine adjourns at once and keeps the lot, bids and time (plan Q-3); sheet copy follows the spec
