---
feature: 2026-11-02-21-push-notifications
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-21-push-notifications main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-21-push-notifications

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-100; module: decided in plan
- 2026-11-02 intake: depends on: 2026-11-02-09-auction-controls, 2026-11-02-14-leaderboard, 2026-11-02-15-pwa-offline
- 2026-11-02 intake: why: people miss auction starts, outbids and match results.
- 2026-11-02 intake: who: Managers who opt in.
- 2026-11-02 intake: done-when: web push for auction starting soon, outbid (when off the auction screen) and post-match points summary; the report-card-ready hook is added by report-card.
