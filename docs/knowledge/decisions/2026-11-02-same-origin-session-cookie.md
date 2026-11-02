tags: [auth, cookies, web, csrf]
# The web app reaches the server through a same-origin /api rewrite
Decision: Next.js rewrites `/api/:path*` to the server (`API_BASE_URL`, read at build time), so `pu_session` is a first-party HttpOnly, SameSite=Lax cookie, Secure outside the dev profile. CSRF protection is off: Lax blocks cross-site POST/PATCH cookies and mutating endpoints accept JSON only.
Why: no CORS, and no breakage under third-party-cookie blocking, unlike direct cross-site calls with SameSite=None.
Cost: the web and server must be deployed behind one origin; `API_BASE_URL` must be set when the web app is built.
