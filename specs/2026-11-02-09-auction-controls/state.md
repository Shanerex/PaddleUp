---
feature: 2026-11-02-09-auction-controls
phase: build
status: draft
g1: approved:Shanerex:2026-11-17
g2: approved:Shanerex:2026-11-17
g3: none
bump: none
next: dev builds tasks.md in order; stops at T-17 for design approval
---
# State: 2026-11-02-09-auction-controls

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-46, FR-47, FR-48 (audit view); module: auction
- 2026-11-02 intake: depends on: 2026-11-02-08-eligibility-guard
- 2026-11-02 intake: why: real auctions need a moderator and a dispute trail.
- 2026-11-02 intake: who: Commissioner (controls); all Managers (read the audit log).
- 2026-11-02 intake: done-when: pause/resume, skip lot, re-open a closed lot (audited), adjourn and resume across days; unsold pool plus accelerated round with nominations; the audit log is viewable by every manager.
- 2026-11-10 intake: deferred from 2026-11-09-home-redesign (R-12), drawn in docs/design/source but not built there: auction screen adjourned button, and the lobby Start now button.
- 2026-11-17 intake: deferred from 2026-11-02-07-auction-screen (plan R-11/AC-16): the paused and adjourned notices (3d/3e) are built against guessed fixture phases `paused`/`adjourned` behind a mapped status, and any unknown phase locks bidding; this spec defines the real engine phases and events, wires the notices to them and adds their e2e.
- 2026-11-17 intake: design refs so far: Room `commish` (AUCTION CONTROLS list), Room `audit` (filters All/Bids/Lifelines/RTM/Admin/Scoring), Room `lobby`/`lobbyReschedule` (Start now), Auction A `paused`, `adjourned`, `unsold`; AuctionNotices paused notice and replay toast.
- 2026-11-17 intake: design refs drawn (P-1, approved 2026-11-17 by Shanerex): Auction A Explorations 3n-3y (controls, pause, skip, re-open, adjourn, accelerated round) and App Screens 9a-9c (commishLive, audit with Passes, auditEmpty). Rules the mocks assume, to confirm in requirements: Skip only before the first bid; a re-opened lot comes back after the current one, from base; Resume from adjourned goes through a 2-minute lobby; up to 3 picks per franchise; pick counts visible to the Commissioner only; accelerated lots go most-picked first.
- 2026-11-17 requirements: pm pass 1 brief; answers Q-1 skip before first bid, Q-2 re-open after current from base, Q-3 2-minute resume lobby, Q-4 picks up to open squad slots (no fixed 3), Q-5 pick counts visible to all, most-picked first; mock text for Q-4/Q-5 needs a source edit
- 2026-11-17 requirements: pm pass 2, requirements.md final (57 lines); awaiting g1
- 2026-11-17 requirements: open slots = squad max minus held; g1 approved by Shanerex
- 2026-11-17 plan: architect pass 1 (95-line plan, 30 tasks, no split); answers Q-1 Commissioner opens accelerated round, Q-2 end after last gap, Q-3 adjourn keeps lot/bids/time, Q-4 advisor ACs (AC-5, AC-12) deferred to 2026-11-02-26-advisor-lifelines, Q-5 non-member 404; pm patching requirements
- 2026-11-17 plan: pm patched requirements (main-done phase R-14/R-15/AC-15, AC-5/AC-12/R-10 deferred to 26, AC-13 404); architect pass 2 final (97-line plan, 31 tasks); awaiting g2
- 2026-11-17 plan: End round = close picks and run picked lots most-picked first (none: auction ends); g2 approved by Shanerex
