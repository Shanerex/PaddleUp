# Architecture

Source: specs/2026-11-01-project-scaffold/plan.md (proposed; pending review); Sport Packs section from specs/2026-11-02-02-sport-pack-framework/plan.md; Rooms section from specs/2026-11-02-04-rooms/plan.md; Auction engine from specs/2026-11-02-06-auction-engine/plan.md; Scoring from specs/2026-11-02-11-scoring-engine/plan.md.

## Repo layout
- `server/`: Spring Boot modular monolith, single Maven module (Java 21, Maven wrapper).
- `web/`: Next.js + TypeScript installable PWA.
- `packs/`: versioned Sport Pack JSON.
- Root: `Makefile`, `compose.yaml`, `scripts/`, `README.md`.

## Server modules
- Base package `com.paddleup`, one top-level package per module: `accounts`, `rooms`, `auction`, `scoring`, `packs`, `advisor`.
- Inside each module, MVCS layers as sub-packages: `controller` (REST/STOMP entry points) -> `service` (logic, transactions) -> `repository` (Spring JDBC; in `packs`, read-only file access); `model` (domain types, rows) and `dto` (request/response/event payloads). Three support packages sit beside them: `exception` (module error types and its `@RestControllerAdvice`), `config` (`@Configuration` and `@ConfigurationProperties`) and `util` (stateless helpers). Other modules use only a module's `service`, `dto` and `exception` packages; `config` and `util` are module-private.
- Calls flow controller -> service -> repository only. Controllers return dtos, never model types.
- Cross-module: a module may use another module's `service` and `dto` packages only, never its controller, repository or model. Enforced by `ModuleBoundariesTest`, a source scan (not ArchUnit) with a self-test: other modules may touch only `service`, `dto` and `exception`; only the top-level `com.paddleup.config` package is exempt. Spring Modulith is still deferred.
- Feature build order and module ownership per feature: `docs/roadmap.md`.

## Sport Packs
- Packs are loaded once at startup from `paddleup.packs.dir` (default `../packs`, env `PADDLEUP_PACKS_DIR`) into an immutable in-memory registry. There is no DB table and no hot reload, and an invalid pack aborts startup.
- Other modules read packs only through `packs.service.PackService` and `packs.dto`.
- Data adapters are beans implementing `packs.service.DataAdapter`, selected by the pack's `adapterKey` == `key()`.
- Deployment: the server image or working dir must contain the packs dir. Without it the server starts with zero packs and logs a WARN.

## Rooms
- A room's config and its pack vocabulary are snapshotted as one JSONB value (with an int `config_version`) when the room is created. The room never re-reads the pack afterwards. Saves use version compare-and-set (409 `stale_version`); config is capped at 64 KB and read back through `rooms.util.StoredJson`.
- Membership = the Commissioner column on the room plus franchise owners. Non-members get 404, non-Commissioners get 403 on Commissioner endpoints.
- `room_log` is append-only, enforced by a DB trigger that blocks UPDATE and DELETE.
- Other modules change room state only through `rooms.service.RoomLifecycle`. REST cannot start the auction.
- The auction reads a room only through `rooms.service.AuctionRoomPort` (access, auctionRefusal, liveInputs, markScheduled). Config carries `openSeconds`, `bidSeconds` and `gapSeconds` (never null on read: absent means 20, 10 and 5 s; ranges 5-120, 3-60, 0-30). Non-members get 404 and non-Commissioners 403 on the auction routes too.
- Config `scoringRules` is the immutable pack-default snapshot taken at creation, and the pack snapshot carries `appearanceRule`; `saveConfig` never changes either. Scoring reads a room only through `rooms.service.ScoringRoomPort` (access, requireCommissioner, ruleInputs, players, roomsOnDataset).
- Invites are a revocable 12-char code or link that expires after 7 days; joining is throttled per user and per IP.

## Auction engine
- One single-writer runner per unended auction holds its state in memory. Every intent, timer step and schedule change is a task on its serial queue; arrival order is queue order.
- `auction_events` (PK room_id+seq, append-only by trigger) is the only source of truth. Each change is persisted, applied, broadcast, then replied to; boot replays the log through the pure `AuctionReducer`. `auctions` is only a boot index; `auction_lots` holds the card fields frozen at go-live.
- Auctions run on one server instance; a second writer fails on the seq key and cannot fork the log. A refused start logs `start_refused` once and stays scheduled until the Commissioner reschedules.
- Timers: the server `Clock` plus a 100 ms tick (`paddleup.auction.tick`, 0 turns it off). Intents close overdue lots first, so `lot_closed` is exact. Tests step a mutable Clock.
- Realtime: STOMP at `/api/ws`, broadcasts on `/topic/auctions/{roomId}` only; bids and passes are REST. Two ways in through one auth check (`StompAuth`): the session cookie on a same-origin handshake, or a single-use 30 s ticket from `POST .../auction/ticket` on CONNECT (Vercel does not proxy upgrades). SEND is always refused. The origin allow-list is `paddleup.auth.web-base-url`.

## Scoring
- The room's edited rule set, its version (compare-and-set, 409 `stale_version`), `frozen_at` and the append-only `scoring_rule_log` (DB trigger blocks UPDATE, DELETE and TRUNCATE) live in scoring. No row, or a row with NULL rules (version 0), means the pack default.
- Matches and provider stats are stored once per season dataset and shared by every room on it. Manual stats are sparse per-room overrides (override beats provider). Room players map to dataset players through `source_player_id`; added players take manual stats only.
- Freeze = server `Clock` at or after the earliest `starts_at` of the room's dataset matches. It is checked under the room's rule-set row lock on every write and persisted per room once seen, so a later reschedule cannot thaw it; the freeze is recorded before the stale-version check. After the freeze the only path is a reasoned, attested, logged change that recomputes the room.
- `PointsEvaluator` is pure. `scoring_points` is replaced per (room, match, player). Every write path and every recompute takes the room's rule row first, then its points rows, so a recompute never overwrites newer rules with older totals.
- Stat and fixture writes are a service API (`MatchStatsService`) with no REST route yet; the ingestion and manual-entry specs add routes with authz and validation.

## Shared infrastructure
- `accounts.service.ClientAddress` is the one way to resolve the client IP (also used for throttle keys).
- `java.time.Clock` (UTC) is an app-wide bean that every module injects by type; tests replace it.
- `com.paddleup.config.JsonLimitsConfig` sets app-wide Jackson limits (depth 64, document 256 KB, string 16 KB); over-limit bodies give 400.

## Web sign-in return
- A signed-out page request redirects to `/sign-in?next=<safe path>`. The path is kept in localStorage (1 h, read once, re-checked on read), and every sign-in path returns to it. Google's success redirect goes to `/auth/continue`.

## Persistence
- PostgreSQL 17: local via docker compose, tests via Testcontainers (same image).
- Schema changes only through Flyway migrations in `server/src/main/resources/db/migration`, starting from a no-op `V1__baseline.sql`.

## Local dev topology
- Postgres in Docker on 5432 (`POSTGRES_PORT` overrides).
- Server on 8080 and web on 3000, both on the host, started together by `make dev` (`make -j2`).

## Quality gates
- `make lint` = Spotless check (server) + ESLint (web) + `scripts/check-agnostic.sh` (grep for specific sport, role, stat and currency names, not the generic concept words, over `server/src/main` and `web/src`; self-tested by `scripts/test-check-agnostic.sh`).
- `make test` = `./mvnw verify` (JUnit 5 + Testcontainers) then Vitest.
