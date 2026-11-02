tags: [requirements, auth, session, lifecycle, verification]
Accounts shipped with all 15 ACs green yet a stale cookie skipped sign-in and the cookie died on browser quit: the ACs listed features (sign in, sign out), never the session's states.
For anything with a lifetime (sessions, bid timers, advisor sessions, lifelines), write a matrix before the ACs: every state (absent, valid, expired/revoked, backend down) x every entry point (protected page, sign-in page, link, API, new tab, restart), plus how long it lives and whether it slides.
Each empty cell becomes an AC or an explicit out-of-scope line. Framework defaults (e.g. Spring Session's browser-session cookie) count as decisions and must be named.
