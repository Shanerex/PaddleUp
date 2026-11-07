---
feature: 2026-11-02-05-player-pool
phase: done
status: approved
g1: approved:Shanerex:2026-11-04
g2: approved:Shanerex:2026-11-04
g3: approved:Shanerex:2026-11-04
bump: minor
next: open a PR; a human merges
---
# State: 2026-11-02-05-player-pool

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02..04 intake/requirements: feature seeded from PRD (FR-20..22; why/who/done-when logged); depends on 03-cricket-t20-pack and 04-rooms; pm final 14 R, 18 AC; g1 approved
- 2026-11-04 requirements: g1 approved by Shanerex; phase plan; screens to draw before architect (R-13)
- 2026-11-04 plan: drew pool, set order and admin import screens (Room pool*/sets*, AdminImport.dc.html, App Screens turn 7); awaiting design approval before architect
- 2026-11-04 plan: design approved by Shanerex (App Screens turn 7); architect pass 1 spawned
- 2026-11-04 plan: answers: datasets in packs + copies in rooms; base price >0, <=purse, on ladder; existing sets only (empty sets kept); fixed AC-14/AC-16/R-13 design file refs (wording only)
- 2026-11-04 plan: architect pass 2 final (26 tasks); awaiting g2
- 2026-11-04 plan: g2 approved by Shanerex; phase build; rx:dev spawned
- 2026-11-04 build: T-1..T-26 done; make lint and make test green (server verify + 184 web tests); phase verify
- 2026-11-04 build: Playwright on a scratch stack (server :8081, own DB): import, pool add/edit/remove/reset, sets, member read-only, stale tab, auction_live lock, 600-row pool (first 100 rows 1.2 s dev, all 600 after scroll 1.2 s); screenshots at 390 and 1440 beside the mocks
- 2026-11-04 build: drift: admin denied copy says "manages" not "runs" (check-agnostic bans the word); importer email omitted from version rows; price shown as pack unit (Crore), not the mock's lakh/crore
- 2026-11-04 verify fix loop 1: mock aligned to code (D-1 manages, D-2 no importer email, D-3 pack-unit price), AC-18 shots in shots/; deferred S-1 (admin CSRF relies on SameSite=Lax; add Origin/header check) and S-2 (no per-user room cap for 2,000-row pool copy)
- 2026-11-04 verify: qa and security pass after fix loop 1 (S-1, S-2 minor deferred); g3 approved by Shanerex; bump minor; phase close
- 2026-11-05 close: decisions/learnings already written; rx-check done: only server/pom.xml shared file modified (jackson-dataformat-csv, planned in plan.md); human decides
- 2026-11-05 close: Shanerex accepted server/pom.xml shared-file change (jackson-dataformat-csv, per plan.md); phase done
- 2026-11-07 review fix (PR #7): added R-15..R-18, AC-19..AC-24 for squad-fill start rule, purse-change block, unfit season data (log, reasons); drew poolUnfit, poolUnfitPack, poolEmptyUnfit, poolEmptyUnfitPack in Room.dc.html + App Screens 7p..7s; awaiting human approval of the mock and of the amended requirements
