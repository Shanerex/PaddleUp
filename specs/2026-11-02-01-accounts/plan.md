---
status: final
---
# Plan: 2026-11-02-01-accounts

## Approach
Build the `accounts` module (R-7, R-8) with Spring Security as the single authentication gate. Server-side sessions come from Spring Session JDBC and are stored in Postgres tables created by Flyway. Spring Security keeps the security context in the session, so the caller is derived only from the `pu_session` HttpOnly cookie (R-5, R-6; AC-4, AC-5, AC-15).
Email sign-in sends one message containing both a single-use link and a 6-digit code backed by the same login-token row (R-1, R-12; AC-1, AC-2, AC-14). Google sign-in uses server-side oauth2Login, links by verified email only, and rejects unverified emails (R-2, R-11; AC-3, AC-13).
Profile edits go through `PATCH /api/me`, which sanitises the name and checks the avatar against an allowlist (R-3, R-4; AC-6 to AC-8). The web app reaches the server through a same-origin Next.js `/api` rewrite and uses springdoc-generated types (R-9; AC-11).

## Options considered
- Session store:
  - (a) Our own `sessions` table with a hashed opaque token and a custom cookie filter. Explicit schema, but more hand-written security code.
  - (b) Spring Session JDBC, with its Postgres schema applied by Flyway and `initialize-schema=never` [chosen, human Q1]. Less custom code, uses the standard HttpSession and SecurityContext path, and Flyway stays the only schema owner.
- Google flow:
  - (a) Spring Security `oauth2Login`, authorization-code flow with a success handler [chosen, Q2]. No token handling in the browser.
  - (b) Google Identity Services ID-token POST. Simpler to test, but adds a Google script to the PWA.
- Cookie origin:
  - (a) Next.js rewrites `/api/*` to the server, giving a first-party `SameSite=Lax` cookie [chosen, Q3]. No CORS, and CSRF is mitigated by Lax plus JSON-only mutating endpoints.
  - (b) Direct cross-site calls with `SameSite=None`, CORS and a CSRF token. Breaks under third-party-cookie blocking.

## Design
- Server dependencies to add to `server/pom.xml` (missing today):
  - `spring-boot-starter-security`
  - `spring-boot-starter-oauth2-client`
  - `spring-session-jdbc`
  - `springdoc-openapi-starter-webmvc-ui` (2.8.x, compatible with Boot 3.5)
  - `org.jsoup:jsoup` (strips tags from names)
  - Test scope: `spring-security-test`, `org.wiremock:wiremock-standalone`
- Migrations in `server/src/main/resources/db/migration`:
  - `V2__spring_session.sql`: `SPRING_SESSION` and `SPRING_SESSION_ATTRIBUTES` plus their indexes, copied verbatim from spring-session-jdbc `schema-postgresql.sql`. The framework stores epoch-millis BIGINT columns, which are UTC by definition.
  - `V3__accounts.sql`, with `timestamptz` columns mapped to `Instant`:
    - `users(id uuid pk, email text unique (lower-cased), display_name text not null, avatar text not null, google_picture_url text null, created_at, updated_at)`. No room column and no room limit (R-8, AC-9).
    - `user_identities(user_id fk, provider text, subject text, created_at, unique(provider, subject))`.
    - `login_tokens(id uuid, email text, link_hash text unique, code_hash text, expires_at, used_at null, attempts int, created_at)`. 15-minute TTL, at most 5 code attempts, single use.
- Configuration (`application.yml`):
  - `spring.session.jdbc.initialize-schema: never`, `spring.session.timeout: 30d` (sliding on access), and the default cleanup cron.
  - A `CookieSerializer` bean (`DefaultCookieSerializer`): name `pu_session`, HttpOnly, SameSite=Lax, Path=/, Max-Age equal to `spring.session.timeout` (30d, so sign-in survives a browser restart), and Secure driven by `paddleup.auth.cookie-secure` (true by default, false in `application-dev.yml`). `SessionCookieRenewalFilter`, ordered just after `SessionRepositoryFilter`, re-sends it on every valid-session request so Max-Age slides with the session (AC-20).
  - `paddleup.auth.*`: token TTL, web base URL, avatar preset count.
  - `paddleup.email.resend.api-key` and `from`.
  - `spring.security.oauth2.client.registration.google`: scopes openid, email and profile. Secrets come from env. The redirect URI is `${paddleup.auth.web-base-url}/api/login/oauth2/code/google`.
- `com.paddleup.accounts`:
  - `model`: `User`, `LoginToken`, `Identity`, and `AccountPrincipal` (a Serializable record holding only the user id; it is the session's principal).
  - `repository`: `UserRepository`, `LoginTokenRepository`, `IdentityRepository` (Spring JDBC `JdbcClient`).
  - `service`:
    - `EmailSignInService` and `GoogleSignInService`.
    - `SignInSessionService`: on success it builds the `AccountPrincipal` authentication, saves it through `HttpSessionSecurityContextRepository` and changes the session id.
    - `ProfileService`.
    - `DisplayNameSanitiser`: NFC, jsoup `Safelist.none()`, strip control characters, collapse whitespace, then check the 2-30 length.
    - `AvatarPolicy`: preset ids `preset-01..preset-NN`, or exactly the stored Google picture URL.
    - The `EmailSender` port, with `ResendEmailSender` by default and `ConsoleEmailSender` under the `dev` profile.
    - `CurrentUser`: public, reads the SecurityContext and returns `UserId`. It is the only way other modules get the caller.
  - `dto`: `UserId`, `ProfileDto`, `UpdateProfileRequest`, `EmailStartRequest`, `EmailVerifyRequest`, `AvatarPresetsDto`.
  - `controller`:
    - `AuthController`: `POST /api/auth/email/start`, and `POST /api/auth/email/verify` taking `{token}` or `{email, code}`.
    - `MeController`: `GET` and `PATCH /api/me`, `GET /api/avatars`.
  - Security config, a `SecurityFilterChain`:
    - 401 JSON entry point.
    - Logout at `POST /api/auth/sign-out`: invalidates the session, which deletes the `SPRING_SESSION` row, clears the cookie and returns 204.
    - oauth2Login endpoints moved under `/api` (`/api/oauth2/authorization/google`, `/api/login/oauth2/code/*`). Its success handler calls `GoogleSignInService`, then replaces the OIDC authentication with `AccountPrincipal` so the session holds only the user id, then redirects to the web app.
    - Permitted without a session: `/api/auth/email/**`, the oauth2 endpoints, `/actuator/health`, `/v3/api-docs`.
    - Request headers and params never set the principal (AC-5).
    - A failed verify never calls `getSession(true)` (AC-2).
- Web (`web/src`):
  - `next.config` rewrites `/api/:path*` to the server.
  - `api/generated.ts` is produced from springdoc by `npm run gen:api` (openapi-typescript). `api/client.ts` makes the API calls.
  - `middleware.ts` checks the `pu_session` cookie with the server (`GET /api/me`) on every page request. No cookie or a 401 redirects to `/sign-in` and clears a rejected cookie. A valid session on `/sign-in` or `/` goes to `/profile`. An unreachable server lets the page through, so an outage never looks like a sign-out.
  - Pages:
    - `app/sign-in/page.tsx`: email request, code entry, Google button.
    - `app/auth/verify/page.tsx`: the link lands here and the page POSTs the token.
    - `app/profile/page.tsx`: name, avatar grid, sign-out.
  - Design tokens come from `globals.css`. Preset avatars are neutral images in `web/public/avatars/` (R-10).
- Tests: Testcontainers integration tests per AC. AC-4 and AC-15 assert the `SPRING_SESSION` row. WireMock covers the Resend API and the Google token and JWKS endpoints. Vitest with Testing Library covers the web pages.

## Architecture changes
- Add an "Identity and sessions" section:
  - Spring Security is the only auth gate, and sessions use Spring Session JDBC.
  - The cookie is `pu_session`: HttpOnly, SameSite=Lax, Max-Age 30 days, and Secure outside dev.
  - The principal is `AccountPrincipal`, which holds the user id only.
  - Other modules get the caller only through `accounts.service.CurrentUser` and `accounts.dto.UserId`, never from request input.
- Persistence: Flyway is the only schema owner, framework tables included. Spring Session tables live in `V2__spring_session.sql` with `spring.session.jdbc.initialize-schema=never`.
- Local dev topology: the web app calls the server through the same-origin Next.js rewrite `/api/*` (locally and on Vercel). OAuth endpoints live under `/api`.
- New "API types" section: springdoc OpenAPI at `/v3/api-docs`. Web types are generated into `web/src/api/generated.ts` and checked in.
- New "Outbound email" section: the `EmailSender` port, with Resend in prod and a console logger under the `dev` profile.

## Risks
- Spring Session tables use BIGINT epoch-millis, not `timestamptz`. Mitigation: AC-10 asserts `timestamptz` and `Instant` on the accounts tables only, and the plan records this deviation.
- oauth2Login stores the authorization request in the HttpSession, so it creates a session row before sign-in. Mitigation: Spring Security changes the session id on success; the cleanup cron expires abandoned rows.
- Serialized OIDC tokens bloat `SPRING_SESSION_ATTRIBUTES`. Mitigation: the success handler swaps in `AccountPrincipal`.
- Mail scanners prefetch magic links and consume them. Mitigation: the link opens a web page that POSTs the token, and the code works as a fallback.
- OTP brute force. Mitigation: 6-digit code, at most 5 attempts per token, 15-minute TTL, hashed at rest. Per-email request throttling is noted as follow-up.
- Name length can change during sanitising. Mitigation: validate the length after sanitising; the AC-7 test documents this.
