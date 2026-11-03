---
feature: 2026-11-02-04-rooms
phase: build
status: approved
g1: approved:Shanerex:2026-11-03
g2: approved:Shanerex:2026-11-03
g3: none
bump: minor
next: design-fidelity rebuild T-26..T-32, then re-verify and g3
---
# State: 2026-11-02-04-rooms

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: pre-seeded from PRD (roadmap order); scope FR-10..13, FR-15, NFR-7 invites; module rooms; deps 01-accounts, 02-sport-pack-framework
- 2026-11-02 intake: why: friend groups need private leagues with their own rules, prefilled from a pack.
- 2026-11-02 intake: who: Commissioner (creates, configures, invites) and Managers (join, name a franchise).
- 2026-11-02 intake: done-when: a room is created from pack presets, config is editable and validated until lock, invite code/link (revocable) creates a franchise, the Commissioner can remove a manager or hand over the role, and room states transition explicitly and visibly.
- 2026-11-03 requirements: two-pass pm, final 14 R / 19 AC (commish franchise optional, rejoin allowed, 7-day code expiry); g1 approved by Shanerex
- 2026-11-03 plan: two-pass architect (25 tasks, V5, option A JSONB snapshot, all defaults); invites kept code/link only (in-app invite future idea); g2 approved by Shanerex
- 2026-11-03 build: 25/25 tasks, make test + lint green, Playwright flow checked; deviations accepted by qa (source-scan ModuleBoundariesTest, RoomState in dto, packs endpoint); phase verify
- 2026-11-03 verify: qa pass (19/19 AC), security pass (S-1..S-4 minor); review.md final; awaiting g3 + bump
- 2026-11-03 fix: human-directed loops 1-3 closed S-1..S-10 and T-3/S-9 test-first (caps, StoredJson jsonb readback, 64 KB config cap); make test + lint green
- 2026-11-03 verify: pass 4 PASS (437 server, 103 web); T-3/S-9/S-10 closed; review.md final; awaiting g3
- 2026-11-03 build: human-directed design-fidelity loop: web UI drifted from docs/design/source; rebuild to mocks, gap screens drawn in source and approved first (T-26..T-32); decision 2026-11-03-design-source-fidelity
- 2026-11-03 build: T-26 gap frames approved by Shanerex (App Screens turn 6); human chose optional pack name/summary/icon and richer join preview
