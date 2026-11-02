/** Name of the server's session cookie. Only the server can say whether it is still valid. */
export const SESSION_COOKIE = "pu_session";

/** Where a signed-in visitor lands. */
export const HOME = "/profile";

/**
 * What the server said about the visitor's session: no cookie, a session it knows, a cookie it
 * rejected (expired, signed out, server-side deleted), or no answer at all.
 */
export type SessionState = "absent" | "valid" | "invalid" | "unknown";

const SIGN_IN = "/sign-in";
const SIGN_IN_PAGES = [SIGN_IN, "/auth/verify"];

function under(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

/**
 * Where to send a page request, or null to let it through. An unreachable server does not count as
 * signed out; the page then shows its own error instead of bouncing a signed-in user to sign-in.
 */
export function signInRedirect(pathname: string, session: SessionState): string | null {
  if (under(pathname, "/api")) return null;
  if (SIGN_IN_PAGES.some((page) => under(pathname, page))) {
    // A magic link may be for another account, so only the sign-in page itself bounces.
    return session === "valid" && under(pathname, SIGN_IN) ? HOME : null;
  }
  return session === "absent" || session === "invalid" ? SIGN_IN : null;
}
