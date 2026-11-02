---
status: final
---
# Review: 2026-11-02-01-accounts

## QA
Full re-run after T-18..T-21. Server `./mvnw -q verify` 18 classes, 0 failures; web 40 tests pass; eslint, spotless and check-agnostic green. Session flows driven with curl against live `make dev` (no Playwright run).
- AC-1..AC-15 pass, each backed by a named IT or unit test (AuthFlowIT, GoogleLoginIT, ProfileNameIT, ProfileAvatarIT, AccountsSchemaIT, UserRepositoryIT and others).
- AC-16 pass: redirect.test.ts plus live; bogus and post-sign-out cookie give 307 to /sign-in with pu_session cleared.
- AC-17 pass: valid cookie gets 200 on /profile; `/` and `/sign-in` give 307 to /profile.
- AC-18 pass: Max-Age=2592000 asserted in AuthFlowIT and seen live.
- AC-19 pass: session.test.ts, redirect.test.ts; live with API stopped, cookie gets 200, no cookie gets 307.
- AC-20 pass: AuthFlowIT renewal tests (same id, full Max-Age); sign-out ends with Max-Age=0.
- T-1..T-21 pass; T-18 tests mirror the new packages.
- Gap: `web/src/middleware.ts` (cookie clearing, pass-through) has no automated test; AC-16/17/19 rest on pure-function tests plus the live run.
- Gap: the middleware's own `/api/me` check drops the renewal cookie, so only browser `/api` calls slide the session; page loads alone do not.
- Gap: second-tab case not run in a real browser, as CLAUDE.md asks.
- Note: Next 16 renames `middleware` to `proxy` (warning only).
- Carried: AC-2 yields 429 past the per-IP cap; AC-11 component-level only; `test_globs` says `web/tests/**` but tests live in `web/src/**`.

## Security
No open redirect, no caching of the session check, no session fixation; cookie HttpOnly, SameSite=Lax, Secure outside dev; sign-out clearing is last.
- S-11 minor: oversized or CR/LF cookie forces the fail-open "unknown" state (session.ts:21-24); page shell only, API still 401. Treat malformed cookie as invalid.
- S-12 minor: `API_BASE_URL` unset falls back to localhost (middleware.ts:6); page gating silently off in prod. Fail at startup or alert on "unknown".
- S-13 minor: sliding 30-day session has no absolute cap (SessionCookieRenewalFilter.java:33-36). Add a 90-day cap.
- S-7 minor: `trusted-proxy-hops` defaults to 0; set `PADDLEUP_TRUSTED_PROXY_HOPS` at deploy.
- S-8 minor: attacker can lock a victim's code sign-in for 1h; link and Google still work.
- S-9 minor: plus/dot aliases dodge the 3/email start cap; bounded by per-IP cap.
- S-10 minor: IPs stored as unsalted SHA-256; V4 "no address stored" claim untrue. Use HMAC.
- S-3 minor: `login_tokens` never purged; anonymous OAuth start writes 30-day sessions.
- S-4 minor: email regex accepts `<`, `>`, `"`, `,`.
- S-5 minor: `/v3/api-docs` and springdoc UI public in prod.
- S-6 minor: magic-link token in query string reaches request logs.

## Verdict
pass. No blockers or majors. S-3..S-13 minor, non-blocking.
