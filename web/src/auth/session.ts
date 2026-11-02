import { SESSION_COOKIE, type SessionState } from "./redirect";

const TIMEOUT_MS = 3000;

/**
 * Asks the server whether a session cookie is still good. Called server-side (middleware), so it
 * calls the api directly rather than through the /api rewrite, forwarding only the session cookie.
 */
export async function checkSession(
  cookie: string | undefined,
  apiBase: string,
  fetcher: typeof fetch = fetch,
): Promise<SessionState> {
  if (!cookie) return "absent";
  try {
    const response = await fetcher(`${apiBase}/api/me`, {
      headers: { cookie: `${SESSION_COOKIE}=${cookie}` },
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (response.ok) return "valid";
    return response.status === 401 ? "invalid" : "unknown";
  } catch {
    return "unknown";
  }
}
