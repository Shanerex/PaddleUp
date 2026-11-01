---
status: final
---
# Requirements: 2026-11-01-project-scaffold

## Problem
The repo has docs but no server/, web/ or packs/. The developer has nothing runnable, and later feature specs need a fixed module layout to build on.
Bootstrap an empty but runnable Spring Boot + Next.js + Postgres skeleton driven by a Makefile.

## Requirements
- R-1: server/ is a Java 21 Spring Boot 3 Maven project with the wrapper (./mvnw) and one package per module: rooms, auction, scoring, packs, advisor.
- R-2: The server exposes GET /actuator/health reporting UP only when the database is reachable.
- R-3: Flyway runs on server start and applies a baseline migration V1 to an empty PostgreSQL database.
- R-4: web/ is a Next.js + TypeScript app with a web app manifest, icons and a minimal service worker (no offline caching), so browsers offer to install it as a PWA.
- R-5: packs/ exists as a directory for versioned Sport Packs, empty of pack content.
- R-6: make dev starts Postgres via docker compose, then the server and the web app.
- R-7: make test runs the server verify (JUnit 5 + Testcontainers) then the web tests (Vitest), and each has at least one passing test.
- R-8: make lint runs Spotless check on the server and ESLint on the web.
- R-9: A clean clone needs only the documented prerequisites; the README states them and the three make commands.
- R-10: No core code, schema or UI string names a sport, role, stat or currency (per CLAUDE.md invariants).

## Acceptance criteria
- AC-1: From a fresh clone with prerequisites installed, make lint exits 0.
- AC-2: From a fresh clone, make test exits 0 and reports at least one server test and one web test run.
- AC-3: After make dev, GET /actuator/health on the server returns HTTP 200 with status UP.
- AC-4: With Postgres stopped, GET /actuator/health does not return status UP.
- AC-5: After first start, the flyway_schema_history table lists V1 as successful; a second start applies no new migration.
- AC-6: After make dev, the web app root returns HTTP 200 and the manifest is served with name, icons and display standalone.
- AC-7: The service worker registers successfully in the served web app and no response is served from a service worker cache when offline.
- AC-8: A Lighthouse or equivalent installability check on the served web app reports it as installable.
- AC-9: server/src/main/java contains exactly the packages rooms, auction, scoring, packs, advisor under the base package, each with a package-info or placeholder class.
- AC-10: packs/ exists in git and contains no sport pack JSON.
- AC-11: README documents prerequisites, make dev, make test and make lint, and following it on a clean clone works unchanged.
- AC-12: A grep of server/src/main and web/src (excluding tests) for sport, role, stat and currency terms returns no matches.

## Out of scope
- Any domain code: rooms, auction, bidding, scoring, advisor logic, Fit Engine.
- Sport Pack schema, loader and validation, and the stub second pack.
- STOMP/WebSocket configuration, auth and invites, AI client config.
- Generated API types from OpenAPI, offline caching and push notifications.
- Deployment to Vercel, Fly.io/Railway or Neon.
- CI workflow (non-goal, deferred to a later feature).
- Architecture test such as ArchUnit enforcing module boundaries (non-goal, deferred).
