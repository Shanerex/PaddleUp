---
status: final
---
# Review: 2026-11-01-project-scaffold

## QA
- AC-1 pass: make lint exit 0 in repo and fresh copy (Spotless, ESLint, agnostic grep).
- AC-2 pass: make test exit 0; Vitest 3 passed, 3 server tests (ApplicationIT 2, HealthDownTest 1).
- AC-3 pass: ApplicationIT asserts 200 and UP. QA did not run make dev itself.
- AC-4 pass: HealthDownTest asserts 503 with the DB stopped.
- AC-5 pass: V1 recorded successful; second migrate runs 0.
- AC-6 pass: root 200; manifest has name, standalone, icons 192/512/maskable.
- AC-7 pass: sw.js has no fetch handler or Cache API; offline half is the human's reported pass.
- AC-8 pass (human): not machine-run; human reported pass in DevTools (screenshot warnings only).
- AC-9 pass: exactly rooms, auction, scoring, packs, advisor under com.paddleup, each with package-info.java.
- AC-10 pass: packs/ holds .gitkeep only.
- AC-11 pass: README steps worked on a fresh copy for lint and test.
- AC-12 pass: check-agnostic.sh ok; independent grep clean.
- T-9 note: tasks.md T-9 has no human result line (date, Chrome version, result); lines 20-21 still say pending-human.
- Env note: only JDK 25 was available (release=21). JDK 21 itself never run.
- T-11 note: first make test failed at repackage ("Unable to find main class", stale target jar); not reproducible. Possible stale-target flake.

## Security
- S-1 minor: compose.yaml:8-9 publishes Postgres on all interfaces with password `paddleup`. Bind to 127.0.0.1.
- S-2 minor: application.yml:5-7 falls back to default DB creds when env unset. Fix before first deploy.
- S-3 minor: maven-wrapper.properties:3 has no distributionSha256Sum.
- S-4 minor: root .gitignore lacks `.env*`; only web/.gitignore covers it.
- Checked clean: actuator exposes health only; sw.js has no cache; lockfile integrity hashes; no auth, injection or secrets.

## Verdict
pass. No blocking items. Follow-ups, non-blocking: T-9 result line, JDK 21 run, S-1..S-4.
