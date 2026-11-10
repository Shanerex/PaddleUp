---
feature: 2026-11-02-26-advisor-lifelines
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-26-advisor-lifelines main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-26-advisor-lifelines

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-122, FR-123, FR-124, FR-125, FR-129; module: advisor, auction
- 2026-11-02 intake: depends on: 2026-11-02-17-retention-rtm, 2026-11-02-22-llm-gateway, 2026-11-02-24-fit-engine
- 2026-11-02 intake: why: the rationed Team Table advisor is the headline AI feature.
- 2026-11-02 intake: who: Managers during a live auction.
- 2026-11-02 intake: done-when: typed lifelines with room counts; spend and refund are auction events; a session lasts until the lot fully resolves (incl. RTM); the Fit card is instant and two voices stream as JSON; the leak filter regenerates once then falls back; content is private, audit holds type/lot/time only; the timer never pauses; any failure refunds.
- 2026-11-10 intake: deferred from 2026-11-09-home-redesign (R-12), drawn in docs/design/source but not built there: auction screen Table tile, and the lobby OVERSEAS and LIFELINES house-rule tiles (shared with 08-eligibility-guard).
