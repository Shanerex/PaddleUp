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
- Sign-in rate limits (`paddleup.auth.throttle.*`, Postgres-backed): 3 emails per address and 10 per client IP per 15 minutes; 5 wrong codes per email (across all its tokens) and 20 failures per client IP per hour; over the limit the server answers 429 with `Retry-After` and sends nothing. Behind proxies set `PADDLEUP_TRUSTED_PROXY_HOPS` to the number of trusted proxies so the client IP is read from `X-Forwarded-For`; the default 0 uses the socket peer.
- API types: with the server running, `cd web && npm run gen:api` regenerates `web/src/api/generated.ts` from `/v3/api-docs` (set `OPENAPI_URL` if the server is not on port 8080). Commit the result.

## Layout

- `server/` Spring Boot modular monolith: modules `accounts`, `rooms`, `auction`, `scoring`, `packs`, `advisor` under `com.paddleup`, each split into `controller`, `service`, `repository`, `model`, `dto`, `exception`, `config` and `util` packages (see `docs/knowledge/architecture.md`)
- `specs/` rx feature specs; `docs/roadmap.md` lists them in build order
- `web/` Next.js + TypeScript installable PWA
- `packs/` versioned Sport Pack JSON (empty for now)
- `compose.yaml`, `Makefile`, `scripts/`
