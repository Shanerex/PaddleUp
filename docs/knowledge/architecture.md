# Architecture

Source: specs/2026-11-01-project-scaffold/plan.md (proposed; pending review); Sport Packs section from specs/2026-11-02-02-sport-pack-framework/plan.md.

## Repo layout
- `server/`: Spring Boot modular monolith, single Maven module (Java 21, Maven wrapper).
- `web/`: Next.js + TypeScript installable PWA.
- `packs/`: versioned Sport Pack JSON.
- Root: `Makefile`, `compose.yaml`, `scripts/`, `README.md`.

## Server modules
- Base package `com.paddleup`, one top-level package per module: `accounts`, `rooms`, `auction`, `scoring`, `packs`, `advisor`.
- Inside each module, MVCS layers as sub-packages: `controller` (REST/STOMP entry points) -> `service` (logic, transactions) -> `repository` (Spring JDBC; in `packs`, read-only file access); `model` (domain types, rows) and `dto` (request/response/event payloads). Three support packages sit beside them: `exception` (module error types and its `@RestControllerAdvice`), `config` (`@Configuration` and `@ConfigurationProperties`) and `util` (stateless helpers). Other modules use only a module's `service`, `dto` and `exception` packages; `config` and `util` are module-private.
- Calls flow controller -> service -> repository only. Controllers return dtos, never model types.
- Cross-module: a module may use another module's `service` and `dto` packages only, never its controller, repository or model. Enforcement (ArchUnit / Spring Modulith) is deferred.
- Feature build order and module ownership per feature: `docs/roadmap.md`.

## Sport Packs
- Packs are loaded once at startup from `paddleup.packs.dir` (default `../packs`, env `PADDLEUP_PACKS_DIR`) into an immutable in-memory registry. There is no DB table and no hot reload, and an invalid pack aborts startup.
- Other modules read packs only through `packs.service.PackService` and `packs.dto`.
- Data adapters are beans implementing `packs.service.DataAdapter`, selected by the pack's `adapterKey` == `key()`.
- Deployment: the server image or working dir must contain the packs dir. Without it the server starts with zero packs and logs a WARN.

## Persistence
- PostgreSQL 17: local via docker compose, tests via Testcontainers (same image).
- Schema changes only through Flyway migrations in `server/src/main/resources/db/migration`, starting from a no-op `V1__baseline.sql`.

## Local dev topology
- Postgres in Docker on 5432 (`POSTGRES_PORT` overrides).
- Server on 8080 and web on 3000, both on the host, started together by `make dev` (`make -j2`).

## Quality gates
- `make lint` = Spotless check (server) + ESLint (web) + `scripts/check-agnostic.sh` (grep for specific sport, role, stat and currency names, not the generic concept words, over `server/src/main` and `web/src`; self-tested by `scripts/test-check-agnostic.sh`).
- `make test` = `./mvnw verify` (JUnit 5 + Testcontainers) then Vitest.
