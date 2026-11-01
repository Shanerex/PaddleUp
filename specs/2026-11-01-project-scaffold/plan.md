---
status: final
---
# Plan: 2026-11-01-project-scaffold

## Approach
Greenfield: no code exists under server/src/main, web/src or packs, and there is no architecture.md and no decision record yet.
Build three independent roots (server/, web/, packs/) plus a root Makefile and compose.yaml, then wire make dev/test/lint (R-6, R-7, R-8).
The server stays to framework defaults: actuator's DataSource health indicator gives R-2/AC-3/AC-4, and Flyway auto-config gives R-3/AC-5. There is no custom health code.
The web app uses the Next.js App Router with a metadata-route manifest and a hand-written service worker that never caches (R-4, AC-6..AC-8).
Every AC except AC-8 and the offline half of AC-7 is either automated in make test/lint or checked by a scripted command.
Manual verification (human-approved): AC-8 is the "equivalent" check in Chrome DevTools. The Application > Manifest pane must show no installability errors, and the browser must offer the install prompt. The result is recorded in T-9. requirements.md is unchanged.

## Options considered
- (chosen, human-approved) Postgres in docker compose; the server (./mvnw spring-boot:run) and web (npm run dev) run on the host in parallel via `make -j2`. Fast reload and no Dockerfiles, but the host needs JDK 21 and Node.
- Run all three in docker compose. Host prerequisites shrink to Docker, but this needs server/web Dockerfiles and gives a slow rebuild loop. Containerising is closer to deployment, which is out of scope.
- Add a root package.json with `concurrently` to drive both processes. Logs are nicer, but it adds a third Node project and a dependency only for dev orchestration.

## Design
- Root: `Makefile` (targets dev, test, lint, plus helpers db-up, server-dev, web-dev, web-install), `compose.yaml` (service `postgres`, image postgres:17, port from POSTGRES_PORT default 5432, db/user/password `paddleup`, pg_isready healthcheck; make dev uses `docker compose up -d --wait`), `.gitignore` (target/, node_modules/, .next/, .DS_Store), `README.md`, `scripts/check-agnostic.sh`.
- make lint (human-approved) runs Spotless check, then ESLint, then scripts/check-agnostic.sh. This adds the AC-12 check to R-8's two linters.
- server/: single Maven module, Spring Boot 3.5.x, Java 21, Maven wrapper. groupId and base package `com.paddleup` (human-approved).
  - Dependencies: spring-boot-starter-web, -actuator, -jdbc, org.postgresql:postgresql, flyway-core, flyway-database-postgresql. Test dependencies: spring-boot-starter-test, spring-boot-testcontainers, testcontainers postgresql + junit-jupiter.
  - Spotless maven plugin with google-java-format, run as `spotless:check` (R-8).
  - `src/main/java/com/paddleup/PaddleUpApplication.java`, plus `package-info.java` in each of `com/paddleup/{rooms,auction,scoring,packs,advisor}`. No other packages (AC-9).
  - `src/main/resources/application.yml`: datasource URL/user/password from env with localhost defaults that match compose; health endpoint exposed (the default). No sport, role, stat or currency strings (R-10).
  - `src/main/resources/db/migration/V1__baseline.sql`: a comment-only no-op baseline. It creates no domain tables.
  - Tests under `src/test/java/com/paddleup/`: `ApplicationIT` with a shared `@ServiceConnection` PostgreSQLContainer (postgres:17), which asserts health is UP, V1 succeeded, and a second `Flyway.migrate()` executes 0 migrations. `HealthDownTest` uses its own container and context, stops the container, and asserts health is not UP (HTTP 503).
- web/: Next.js (current stable, App Router, `src/` dir) + TypeScript, npm with a committed package-lock.json.
  - `src/app/layout.tsx` and `src/app/page.tsx`: neutral placeholder copy.
  - `src/app/manifest.ts`: name, short_name, start_url "/", display "standalone", theme/background colours from docs/design tokens, icons 192/512 plus a maskable icon.
  - `public/icons/*`: the icon PNGs.
  - `public/sw.js`: install calls skipWaiting, activate calls clients.claim. There is no fetch handler and no Cache API use (AC-7).
  - `src/app/sw-register.tsx`: client component that registers /sw.js.
  - `next.config.ts`: `Cache-Control: no-cache` header on /sw.js.
  - `eslint.config.mjs`: flat config with eslint-config-next, run as `eslint .`.
  - `vitest.config.ts` and `src/app/manifest.test.ts` (AC-2, AC-6 fields).
- packs/: `.gitkeep` only (R-5, AC-10).
- `scripts/check-agnostic.sh`: case-insensitive, word-boundary grep over server/src/main and web/src (excluding tests and `*.test.*`). The term list covers the generic words (sport, role, stat, currency), Cricket T20 terms (cricket, batter, bowler, wicket, over, run, all-rounder, keeper, T20), other sports (football, soccer, goal), and currency names/symbols (INR, USD, rupee, crore, lakh, dollar, ₹). It exits non-zero on any match (AC-12).

## Architecture changes
docs/knowledge/architecture.md does not exist yet. I propose the human create it with:
- Repo layout: server/ (Spring Boot modular monolith, single Maven module), web/ (Next.js PWA), packs/ (Sport Pack JSON), root Makefile, compose.yaml and scripts/.
- Base package `com.paddleup`. Exactly one top-level package per module (rooms, auction, scoring, packs, advisor). Modules do not reference each other's internals. Enforcement (ArchUnit/Spring Modulith) is deferred.
- Persistence: PostgreSQL 17 (local via compose, tests via Testcontainers with the same image), schema only through Flyway migrations in server/src/main/resources/db/migration, starting from no-op V1.
- Local dev topology: Postgres in Docker on 5432 (POSTGRES_PORT override); server on 8080 and web on 3000 on the host, started by make dev via `make -j2`.
- Quality gates: make lint = Spotless + ESLint + scripts/check-agnostic.sh (the sport-agnostic term grep).
- Suggested decision records for docs/knowledge/decisions: make dev orchestration (option A); plain packages now, Spring Modulith deferred; the agnostic grep as a lint gate.

## Risks
- Lighthouse 12+ removed the PWA audit. Mitigation: AC-8 is verified by hand in Chrome DevTools (approved), recorded in T-9.
- Some browsers may still require a fetch handler for installability. Mitigation: add a pass-through `fetch` listener that never calls respondWith or caches, so AC-7 still holds.
- Host port 5432 may already be taken by a local Postgres. Mitigation: compose and application.yml read POSTGRES_PORT (default 5432), documented in the README.
- AC-1 and AC-2 on a fresh clone need web/node_modules. Mitigation: lint and test depend on a `web-install` target (`npm ci`).
- Testcontainers needs a running Docker daemon. Mitigation: README lists it as a prerequisite (AC-11).
- A comment-only V1 might be rejected by Flyway. Mitigation: if needed, V1 holds a harmless no-op statement (`SELECT 1;`) and still no domain schema.
- Because the agnostic grep is now in make lint, a false positive (e.g. "over", "run") would fail AC-1. Mitigation: word-boundary matching only, scoped to src/main and web/src; T-10 verifies a clean pass.
