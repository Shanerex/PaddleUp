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

/** Pages a sign-in never returns to: they are part of signing in. */
const NEVER_RETURN_TO = [SIGN_IN, "/auth"];

/**
 * A page path that is safe to return to after signing in, or null. Only a single-slash,
 * same-origin path is accepted: no scheme, no `//`, no backslash, no control characters, so a
 * crafted `next` can never send the visitor to another site.
 */
export function safeNext(raw: string | null | undefined): string | null {
  if (typeof raw !== "string" || raw.length === 0 || raw.length > 512) return null;
  if (!raw.startsWith("/") || raw.startsWith("//")) return null;
  if (/[\\\u0000-\u001f\u007f]/.test(raw)) return null;
  const path = raw.split(/[?#]/)[0];
  if (NEVER_RETURN_TO.some((page) => under(path, page))) return null;
  if (under(path, "/api")) return null;
  return raw;
}

/**
 * Where to send a page request, or null to let it through. An unreachable server does not count as
 * signed out; the page then shows its own error instead of bouncing a signed-in user to sign-in.
 * A signed-out visitor is sent to sign-in with `next` set to the page they asked for, unless that
 * is the home page or not safe to return to.
 */
export function signInRedirect(
  pathname: string,
  session: SessionState,
  search = "",
): string | null {
  if (under(pathname, "/api")) return null;
  if (SIGN_IN_PAGES.some((page) => under(pathname, page))) {
    // A magic link may be for another account, so only the sign-in page itself bounces.
    if (session === "valid" && under(pathname, SIGN_IN)) {
      return safeNext(new URLSearchParams(search).get("next")) ?? HOME;
    }
    return null;
  }
  if (session !== "absent" && session !== "invalid") return null;
  const next = pathname === "/" || pathname === HOME ? null : safeNext(pathname + search);
  return next ? `${SIGN_IN}?next=${encodeURIComponent(next)}` : SIGN_IN;
}

/** Where the page to return to after sign-in is kept; a magic link may open in another tab. */
export const NEXT_KEY = "pu_next";

/** A remembered page is forgotten after this long, so an old visit cannot hijack a later one. */
const NEXT_MAX_AGE_MS = 60 * 60 * 1000;

/** Remembers a safe page to return to after sign-in; anything unsafe is ignored. */
export function rememberNext(raw: string | null | undefined): void {
  const next = safeNext(raw);
  if (!next) return;
  try {
    localStorage.setItem(NEXT_KEY, JSON.stringify({ next, at: Date.now() }));
  } catch {
    // storage blocked: sign-in then lands on the home page
  }
}

/** The remembered page, once: it is cleared as it is read. Null when none, old or unsafe. */
export function takeNext(): string | null {
  try {
    const stored = localStorage.getItem(NEXT_KEY);
    localStorage.removeItem(NEXT_KEY);
    if (!stored) return null;
    const { next, at } = JSON.parse(stored) as { next?: unknown; at?: unknown };
    if (typeof at !== "number" || Date.now() - at > NEXT_MAX_AGE_MS) return null;
    return safeNext(typeof next === "string" ? next : null);
  } catch {
    return null;
  }
}

/** Where to go once signed in: the remembered page, or home. */
export function afterSignIn(): string {
  return takeNext() ?? HOME;
}
