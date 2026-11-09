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

## Sign-in setup

Accounts sign in by emailed link or 6-digit code, or with Google. Sessions are server-side (Postgres) behind an HttpOnly `pu_session` cookie.

- Local dev: `make dev` loads `server/.env.local` if present (git-ignored; `KEY=value` lines for `SPRING_PROFILES_ACTIVE`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` and other local settings). The profile falls back to `dev` when neither that file nor the shell sets it. The `dev` profile prints the sign-in link and code in the server log instead of sending email, and uses a non-Secure cookie. No Resend key is needed.
- Email (Resend), any other profile: set `RESEND_API_KEY` and optionally `RESEND_FROM` (a sender address on a domain verified in Resend).
- Google: create an OAuth client (type Web application) in Google Cloud and set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` for the server. Add the redirect URI `<web base URL>/api/login/oauth2/code/google`, for example `http://localhost:3000/api/login/oauth2/code/google`. Only the `openid`, `email` and `profile` scopes are requested.
- `PADDLEUP_WEB_BASE_URL` (default `http://localhost:3000`) is the public web origin, used in emailed links and the Google redirect.
- The web app proxies `/api/*` to the server. Set `API_BASE_URL` (default `http://localhost:8080`) when building the web app; Next.js bakes rewrites in at build time.
- Sign-in rate limits (`paddleup.auth.throttle.*`, Postgres-backed): 3 emails per address and 10 per client IP per 15 minutes; 5 wrong codes per email (across all its tokens) and 20 failures per client IP per hour; over the limit the server answers 429 with `Retry-After` and sends nothing. The client IP is read from `X-Forwarded-For` behind `PADDLEUP_TRUSTED_PROXY_HOPS` trusted proxies (default 2: Vercel rewrite + Fly proxy; the dev profile uses 0, the socket peer). Check the real header after deploying; a wrong count shares buckets between users but never lets a client choose its own address. A locked email answers 429 with `Retry-After` even for the right code; the emailed link still works because it is not email-throttled.
- API types: with the server running, `cd web && npm run gen:api` regenerates `web/src/api/generated.ts` from `/v3/api-docs` (set `OPENAPI_URL` if the server is not on port 8080). Commit the result.

## Dev test accounts

On a `dev` boot the server seeds four test accounts and one room, `Seed League` (cricket-t20, standard preset), in `setup` with four franchises. Sign in as `cara@paddleup.test` (Commissioner), `alex@`, `bea@` or `chen@` (managers, `@paddleup.test`). The sign-in code is printed in the server log. Cara can schedule the auction from the room. Seeding runs once: a later boot finds the room and skips. Turn off with `paddleup.dev-seed.enabled=false` in `application-dev.yml` or as a `--paddleup.dev-seed.enabled=false` argument. The room has no player pool, so go-live refuses until season data is loaded (see below).

## Season data (player pool)

Each Sport Pack has versioned season datasets. An app admin imports one at `/admin/import`; every room copies the latest version when it is created, and its Commissioner can edit that copy, reset it and reorder its sets until the auction goes live.

- Admins: set `PADDLEUP_ADMIN_EMAILS` to a comma-separated list of account emails (case-insensitive). The default is empty, which admits nobody; the import endpoints answer 403 and the page shows the "app admins only" screen. The `dev` profile needs it too, for example `PADDLEUP_ADMIN_EMAILS=you@example.com make dev`.
- File format: CSV with the header `name,country,role,tags,base_price,set,provider_id` (tags separated by `;`, `provider_id` optional), or JSON `{"players":[{"name":"...","country":"...","role":"...","tags":["..."],"base_price":20,"set":"...","provider_id":"..."}]}`. At most 1 MB and 2,000 rows. Row 1 is the first player, not the header.
- Rules per row: `role` and `tags` must be keys of the chosen pack; `base_price` is a whole number of the pack's smallest currency unit, above zero, at most every preset's purse and on its increment ladder; name, country and set are cleaned of markup and capped at 60, 40 and 40 characters; `provider_id` is up to 64 characters of letters, digits and `. : _ -`.
- Import is partial: valid rows become the next version (v1, v2, ...) and rejected rows are listed with row, field and problem. A file with no valid row creates no version and answers 422 with the report. Versions are immutable; existing room copies never change.
- Rooms without a copy (no season data when they were created) cannot start the auction. The Commissioner uses "Load season data" on the player pool screen once a version exists.

## Auction

The auction runs on one server instance: a single-writer runner per auction applies bids in arrival order and writes every event to an append-only log before it broadcasts. A restart rebuilds each unended auction from its log. Under `/api/rooms/{roomId}/auction` (members only; a non-member gets 404):

- `PUT /schedule` `{startsAt}` (Commissioner): schedule or, before go-live, reschedule a future UTC start. Refused (409 `not_ready`) without a big enough pool copy or enough franchises. The auction goes live at that instant with no request; if the room cannot start it logs one `start_refused`, stays scheduled and waits for a reschedule. Pool, set-order and config writes return 409 from go-live.
- `GET /` full state with `serverNow`, deadline, purses, squads, `lastSeq` and who is connected; `GET /events?after=N&limit=` the log from seq N+1.
- `POST /bids` `{intentId, lot, amount}` and `POST /passes` `{intentId, lot}`. The amount must be the base price, then the current bid plus the step of the ladder band. A rule rejection answers 200 `{outcome: "rejected", reason}` with reason `not_a_franchise`, `leader`, `passed`, `purse`, `outbid` or `lot_closed`; a wrong phase answers 409 `not_live`, `no_open_lot` or `ended`. Resending an `intentId` returns the first answer. Bids are irrevocable: there is no cancel or undo route.
- Timers come from room config (`openSeconds` 20, `bidSeconds` 10, `gapSeconds` 5 by default); clients never send a time. `paddleup.auction.tick` (default `100ms`, `0` turns it off) is the clock step and `paddleup.auction.recovery` rebuilds auctions on boot.
- STOMP at `/api/ws` carries broadcasts only: subscribe to `/topic/auctions/{roomId}` for events (each has a gap-free `seq`; on a jump refetch `GET /events?after=<last seq>` or `GET /`) and `/topic/auctions/{roomId}/presence` for connected franchises. Two connection modes, one auth check: same origin through the `/api` rewrite with the session cookie, or directly to the server origin with a ticket (`POST .../auction/ticket`, single use, 30 s) in a `ticket` CONNECT header. Sending a frame is refused.

## Layout

- `server/` Spring Boot modular monolith: modules `accounts`, `rooms`, `auction`, `scoring`, `packs`, `advisor` under `com.paddleup`, each split into `controller`, `service`, `repository`, `model`, `dto`, `exception`, `config` and `util` packages (see `docs/knowledge/architecture.md`)
- `specs/` rx feature specs; `docs/roadmap.md` lists them in build order
- `web/` Next.js + TypeScript installable PWA
- `packs/` versioned Sport Pack JSON (empty for now)
- `compose.yaml`, `Makefile`, `scripts/`
