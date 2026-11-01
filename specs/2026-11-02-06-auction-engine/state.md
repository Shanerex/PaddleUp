---
feature: 2026-11-02-06-auction-engine
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-06-auction-engine main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-06-auction-engine

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-40, FR-41, FR-42, FR-43, FR-45, FR-48 (event log), FR-50, NFR-1, NFR-2, NFR-4; module: auction
- 2026-11-02 intake: depends on: 2026-11-02-05-player-pool
- 2026-11-02 intake: why: the live auction is the core and the highest-risk component (PRD 8: prototype first with a fake 20-player pool).
- 2026-11-02 intake: who: Managers bidding live; Commissioner scheduling.
- 2026-11-02 intake: done-when: a scheduled auction runs set by set, lot by lot over STOMP intents; increments follow the ladder; T_open/T_bid timers use server time; simultaneous bids resolve by arrival order; every event is persisted before broadcast; a restart resumes from the log; a reconnecting client resyncs full state.
