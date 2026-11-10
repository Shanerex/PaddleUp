---
feature: 2026-11-02-09-auction-controls
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-09-auction-controls main, then /rx:next (pm drafts requirements.md)
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
