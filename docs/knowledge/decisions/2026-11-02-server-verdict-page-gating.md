tags: [auth, session, web, lifecycle]
# Web pages are gated by the server's verdict on the session, not by cookie presence
Decision: Next middleware asks `/api/me` (no-store) and routes on none / valid / invalid / unknown. Invalid clears `pu_session` and redirects to sign-in; unknown (server down, non-401) lets the request through. The session is a persistent 30-day cookie that the server re-sends on every valid request (sliding).
Why: cookie presence kept stale cookies past sign-out or expiry, and Spring Session's default cookie died on browser quit. Fail-open is safe because every `/api` call still returns 401.
Cost: one extra server call per page load; no absolute session cap yet (S-13); the middleware's own check drops the renewal cookie, so only `/api` calls slide it.
