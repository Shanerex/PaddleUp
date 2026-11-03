---
feature: 2026-11-02-04-rooms
phase: verify
status: awaiting-human
g1: approved:Shanerex:2026-11-03
g2: approved:Shanerex:2026-11-03
g3: none
bump: minor
next: /rx:next, human gives g3
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
- 2026-11-03 verify: human chose fix S-1..S-4 before g3; bump minor
- 2026-11-03 fix: S-1..S-4 fixed test-first (loop 1), re-verify pass, new minor S-6; make test + lint green
- 2026-11-03 fix: human chose S-6 (loop 2); fixed test-first (50-entry caps on nested roles/tiers/bands, role-conditional nesting <= 3, Jackson nesting depth 64); make test + lint green
- 2026-11-03 verify: pass 3 (human-directed: S-7, S-8, JSON limits to app config) FAIL on T-3/S-9 stored config readback bricks room
- 2026-11-03 verify: human directed fix of T-3/S-9 in two layers (uncapped DB-read mapper + saved-size cap) + S-10
- 2026-11-03 fix: T-3/S-9/S-10 test-first: StoredJson (rooms.util) reads jsonb without request limits, writes NON_NULL, refuses config >64 KB with invalid_config; scale lower bound -6; make test + lint green
- 2026-11-03 verify: pass 4 PASS (437 server, 103 web); T-3/S-9/S-10 closed; review.md final; awaiting g3
