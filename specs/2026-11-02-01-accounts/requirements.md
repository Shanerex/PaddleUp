---
status: final
---
# Requirements: 2026-11-02-01-accounts

## Problem
Every room, bid and score needs a signed-in identity, and no user model exists yet. Commissioners and Managers must be able to sign in, present a name and avatar to friends, and take part in several rooms. Scope: FR-1, FR-2 (PRD 4.1).

## Requirements
- R-1: A visitor can sign in by email with a single-use, time-limited magic link or OTP code; there are no passwords. First sign-in creates the account.
- R-2: A visitor can sign in with Google OAuth (openid, email, profile scopes only); first Google sign-in creates the account.
- R-3: A signed-in user can set and change a display name of 2-30 characters, sanitised; names are not unique.
- R-4: A signed-in user can set and change an avatar chosen from a preset set or the Google picture URL; image upload is not supported.
- R-5: The server creates a session stored in Postgres at sign-in, sent as an HttpOnly cookie, and derives the caller identity from it on every request; client-supplied user ids are never trusted.
- R-6: Unauthenticated calls to protected endpoints are rejected; sign-out deletes the session server-side.
- R-7: Account data is stored with Instant UTC timestamps via Flyway migration, in the new accounts module (service and dto packages are its only public surface).
- R-8: The user model places no limit on how many rooms one user belongs to; the accounts module exposes a user id other modules can reference (membership itself lives in rooms).
- R-9: The web app offers sign-in, sign-out and a profile screen using the design tokens in docs/design.
- R-10: No sport, role, stat or currency is named in accounts code, schema or UI strings.
- R-11: A Google sign-in whose email is verified by Google and equals an existing account's email signs into that account; an unverified Google email never links.
- R-12: Magic-link/OTP email is sent through Resend; in local dev the message is logged to the console instead, and the Resend key comes from configuration.
- R-13: Session lifecycle: the web app routes every page by what the server says about the session (none, valid, rejected, server unreachable), not by cookie presence; a session lasts 30 days from last use and survives tabs and browser restarts.

## Acceptance criteria
- AC-1: A new email address requests a link or code, redeems it, and reaches a protected endpoint with 200 (R-1, R-5).
- AC-2: A wrong, expired or already-used link or code is rejected with 401 and creates no session (R-1, R-5).
- AC-3: Google callback for a new identity creates exactly one account; repeating it signs into the same account (R-2).
- AC-4: A protected endpoint called without a session returns 401; after sign-out the old session cookie returns 401 and its session row is gone (R-6).
- AC-5: A request carrying a spoofed user id or user header still resolves to the session user (R-5).
- AC-6: Setting a display name of 2 to 30 characters persists and is returned on the next profile read; 1 or 31 characters are rejected with 400; two users may hold the same name (R-3).
- AC-7: A display name containing markup or control characters is stored sanitised (R-3).
- AC-8: Choosing a preset or the Google picture URL persists and is returned on profile read; any other avatar value is rejected with 400 (R-4).
- AC-9: Two distinct room ids can be associated with one user id in a test against the user model without constraint violation (R-8).
- AC-10: Flyway migrates an empty Postgres to the accounts schema; timestamp columns are UTC and map to Instant (R-7).
- AC-11: Web: a signed-out user is redirected to sign-in, and after sign-in can edit and see name and avatar (R-9).
- AC-12: A grep of accounts code, migrations and UI strings finds no sport, role, stat or currency term (R-10).
- AC-13: Google sign-in with a verified email matching an email-created account yields that same user id; with an unverified email it is rejected and no link is made (R-11).
- AC-14: With the dev profile active, requesting a link or code writes it to the console log and makes no Resend call (R-12).
- AC-15: The session cookie has HttpOnly set and its session row is in Postgres (R-5).
- AC-16: Web: a page request whose cookie the server rejects (expired, signed out, unknown) redirects to sign-in and clears the cookie (R-13).
- AC-17: Web: with a valid session, any page in any tab loads without visiting sign-in, and `/sign-in` and `/` redirect to the signed-in home (R-13).
- AC-18: The session cookie's Max-Age equals the server session timeout (30 days), so sign-in survives a browser restart (R-13).
- AC-19: Web: when the server is unreachable, a page request with a session cookie is let through rather than redirected to sign-in (R-13).
- AC-20: Every request with a valid session re-sends the cookie with the same session id and full Max-Age, so an active user is never signed out at day 30; sign-out still clears it (R-13).

## Out of scope
- Profile stats (FR-3, P2).
- Rooms, invites and membership tables (rooms spec).
- Roles such as Commissioner (per-room, rooms spec).
- Passwords, image upload, other OAuth providers.
- Push notifications, account deletion and admin tooling.
