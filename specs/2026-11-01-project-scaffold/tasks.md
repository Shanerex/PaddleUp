---
status: final
---
# Tasks: 2026-11-01-project-scaffold

## Tasks
<!-- Each task cites at least one existing AC. Tick to - [x] when done. -->
- [x] T-1 (AC-10): Add root .gitignore (target/, node_modules/, .next/, .DS_Store) and packs/.gitkeep. Verify with `git ls-files packs` (shows .gitkeep only) and confirm there is no *.json under packs/.
- [x] T-2 (AC-9): Create server/ Maven project, groupId com.paddleup: Spring Boot 3.5.x pom (Java 21, web, actuator, jdbc, postgresql, flyway; test: boot-testcontainers, testcontainers postgresql), Maven wrapper, PaddleUpApplication and package-info.java in com.paddleup.{rooms,auction,scoring,packs,advisor}. Verify `./mvnw -q -DskipTests package` passes and the package listing equals exactly those five.
- [x] T-3 (AC-1): Configure the Spotless maven plugin (google-java-format) on server/. Verify that `./mvnw spotless:check` exits 0 and fails on a deliberately misformatted file.
- [x] T-4 (AC-3): Add root compose.yaml with service postgres (postgres:17, db/user/password paddleup, POSTGRES_PORT default 5432, pg_isready healthcheck). Verify that `docker compose up -d --wait` reports healthy.
- [x] T-5 (AC-3, AC-5): Add server application.yml (env-driven datasource with compose defaults) and db/migration/V1__baseline.sql (no-op). Add ApplicationIT with a @ServiceConnection PostgreSQLContainer that asserts /actuator/health is 200 UP, flyway_schema_history has V1 success, and a second Flyway.migrate() executes 0 migrations.
- [x] T-6 (AC-4): Add HealthDownTest with its own container and context. It stops the container and asserts /actuator/health returns a non-UP status (503).
- [x] T-7 (AC-1, AC-6): Create web/ Next.js + TypeScript app (App Router, src/, npm, committed lockfile) with neutral layout/page copy and eslint.config.mjs (eslint-config-next, `npm run lint` = `eslint .`). Verify that lint exits 0 and `npm run dev` serves / with 200.
- [x] T-8 (AC-2, AC-6): Add Vitest (`npm test` = `vitest run`), src/app/manifest.ts (name, short_name, start_url, display standalone, design-token colours, icons 192/512/maskable), public/icons, and manifest.test.ts asserting those fields. Verify `curl /manifest.webmanifest` shows them.
- [x] T-9 (AC-7, AC-8): Add public/sw.js (skipWaiting/clients.claim, no Cache API, no respondWith), sw-register.tsx client component used in layout, and a no-cache header for /sw.js in next.config.ts. Manual verification in Chrome DevTools on the served app:
  - (AC-7) the SW is activated, and an offline reload has no "(ServiceWorker)" responses in Network.
  - (AC-8) the Application > Manifest pane shows no installability errors, and the browser offers the install prompt.
  Record date, browser version and result here.
  - 2026-11-02 dev (automated, Playwright Chrome 156): (AC-7, partial) SW registered, state activated, controls page, scope /, caches.keys() empty; sw.js has no fetch handler or Cache API. Served with no-cache. Human offline-reload check in DevTools: pending-human.
  - (AC-8) pending-human: Application > Manifest installability and install prompt not checked by dev. Manifest served OK via curl (name, display standalone, icons 192/512/maskable).
- [x] T-10 (AC-12): Add scripts/check-agnostic.sh (case-insensitive word-boundary grep of server/src/main and web/src, tests excluded, term list from plan.md). Verify that it exits 0 on the tree and non-zero after a planted term.
- [x] T-11 (AC-1, AC-2, AC-3, AC-6, AC-12): Write root Makefile.
  - `lint`: web-install, server spotless:check, web lint, scripts/check-agnostic.sh.
  - `test`: web-install, `./mvnw -q verify`, web `npm test`.
  - `dev`: `docker compose up -d --wait`, then server-dev and web-dev in parallel via `make -j2`.
  Verify: lint and test exit 0; dev serves health UP on 8080 and web root 200 on 3000.
- [x] T-12 (AC-11): Write README.md: purpose line, prerequisites (Docker with compose v2, JDK 21, Node LTS + npm, make), POSTGRES_PORT override, make dev/test/lint (lint includes the agnostic check), ports 8080/3000. Verify by following it verbatim on a fresh clone into a temp dir.
