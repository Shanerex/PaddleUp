# PaddleUp

Private, invite-only franchise-style player auction plus season-long fantasy league for friend groups.
Sport-agnostic core; all sport specifics live in Sport Packs (v1 ships Cricket T20 only).
Optional, rationed AI advisor ("Team Table") and post-auction AI Report Card.
Product spec: docs/PaddleUp_PRD.md. UI source of truth: docs/design/README.md.

Not: real-money play, public lobbies, native apps, AI that bids or acts for a manager.

## Stack
- server/: Java 21, Spring Boot 3 modular monolith, Maven wrapper. Modules: accounts, rooms,
  auction, scoring, packs, advisor. STOMP over WebSocket for live auction. PostgreSQL + Flyway.
- Spring AI with an OpenAI-compatible client (configurable base_url/model; Ollama for local dev).
- web/: Next.js + TypeScript installable PWA. API types generated from springdoc OpenAPI.
- packs/: versioned JSON Sport Packs, validated on load. A stub football pack lives in tests.
- Hosting: web on Vercel, server on Fly.io/Railway, Postgres on Neon. Target $0-10/month.

## Commands
- make test    server ./mvnw -q verify (JUnit 5 + Testcontainers, needs Docker), then web tests
- make lint    server and web linters
- make dev     local Postgres + server + web

## Conventions
- Server code organised by module package; inside each module, MVCS sub-packages controller,
  service, repository, model, dto, plus exception (module error types and its @RestControllerAdvice),
  config (@Configuration and @ConfigurationProperties) and util (stateless helpers). Other modules
  use only a module's service, dto and exception packages; config and util stay module-private.
- REST for commands and queries, WebSocket for auction broadcasts only.
- Every schema change is a Flyway migration; never edit an applied one.
- Times stored UTC (Instant), rendered local.
- Frontend uses the design tokens in docs/design; numbers that change live use DM Mono tabular.
- Docs are part of the deliverable: README, decision records in docs/knowledge/decisions.

## Hard invariants
- Server is the only authority on auction state; clients send intents only. Trust no client
  clock or state.
- Every auction event (bids, passes, RTM, lifeline spend/refund, admin actions) is persisted to
  the append-only event log before broadcast; restart resumes from the log.
- Bids are irrevocable. The bid timer is never paused for the AI advisor.
- No core code, schema or UI string names a sport, role, stat or currency; read them from the
  room's Sport Pack. The stub second pack must pass with zero core changes.
- Fit Engine is deterministic plain Java; the LLM only explains its output.
- Advisor never sees upcoming lots, the remaining pool or set order; outputs pass the leak filter.
  Advisor content is private; the audit log records lifeline type, lot and time only.
- User-editable text is sanitised and length-capped before entering a prompt; LLM output is
  schema-validated. LLM failure never affects the auction and refunds the lifeline.
- Scoring recompute is idempotent; manual stat entries beat provider data.
- UI is built to a named screen in docs/design/source/*.dc.html, cited as "Design refs" in tasks.md.
  No screen there: draw it in the source and get approval first; never invent layout in code. Spec
  sets rules and limits, the mock sets the look. rx spawn prompts for UI work include those files.

## rx
Workflow: /rx:next on a pre-seeded spec in specs/<id>/ (not /rx:feature); lane, build order and
how to start are in docs/roadmap.md, formats in the rx-spec skill. Requirements: anything with a
lifetime gets a state x entry-point matrix before its ACs (learnings/2026-11-02-state-matrix-for-lifecycles).
Scope freezes at G2: later finds go in this spec's own state.md as "deferred to <id>: ...". A plan
over 100 lines or 40 tasks is split before G2. architecture.md changes are a patch under plan.md
"Architecture changes", never a task. Verify: the AC tests; /code-review on the branch (findings join
the fix loop, the PR opens after G3); the changed flows in a real browser (Playwright, make dev) incl.
new tab and stale state; each changed screen beside its mock at 390 and 1440 (decisions/2026-11-03-design-source-fidelity).
