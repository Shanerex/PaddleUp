tags: [auth, oauth, google, security]
# Google sign-in is the server-side oauth2Login code flow
Decision: Spring Security `oauth2Login` with endpoints under `/api` and a redirect URI built from `paddleup.auth.web-base-url`. A success handler maps verified ID-token claims to an account, swaps the OIDC authentication for `AccountPrincipal`, and redirects to the web app. No Google tokens are kept (authorized-client repository discards them). An unverified email is rejected and never links.
Why: no token handling in the browser and no Google script in the PWA, unlike the ID-token POST option.
Cost: the authorization request briefly lives in a session before sign-in, so abandoned logins leave rows until the cleanup cron expires them.
