# PaddleUp

Private, invite-only player auction plus season-long fantasy league for friend groups.
Product spec: `docs/PaddleUp_PRD.md`. UI source of truth: `docs/design/README.md`.

## Prerequisites

- Docker with Compose v2 (a running daemon; the server tests use Testcontainers)
- JDK 21 (the Maven wrapper `server/mvnw` downloads Maven itself)
- Node LTS and npm
- make

## Commands

Run from the repo root.

- `make dev` starts Postgres via `docker compose up -d --wait`, then the server (http://localhost:8080, health at `/actuator/health`) and the web app (http://localhost:3000) in parallel. Stop with Ctrl-C; `docker compose down` stops Postgres.
- `make test` runs the server `./mvnw -q verify` (JUnit 5 + Testcontainers), then the web tests (Vitest).
- `make lint` runs Spotless check on the server, ESLint on the web, and `scripts/check-agnostic.sh`, which fails if core code names a sport, role, stat or currency.

If port 5432 is taken, set `POSTGRES_PORT` for both compose and the server, for example `POSTGRES_PORT=5433 make dev`.

## Layout

- `server/` Spring Boot modular monolith: modules `accounts`, `rooms`, `auction`, `scoring`, `packs`, `advisor` under `com.paddleup`, each split into `controller`, `service`, `repository`, `model` and `dto` packages (see `docs/knowledge/architecture.md`)
- `specs/` rx feature specs; `docs/roadmap.md` lists them in build order
- `web/` Next.js + TypeScript installable PWA
- `packs/` versioned Sport Pack JSON (empty for now)
- `compose.yaml`, `Makefile`, `scripts/`
