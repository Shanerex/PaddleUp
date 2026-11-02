import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { SESSION_COOKIE, signInRedirect } from "@/auth/redirect";
import { checkSession } from "@/auth/session";

const apiBase = process.env.API_BASE_URL ?? "http://localhost:8080";

// Next 16 renames this convention to proxy.ts; plan.md names middleware.ts, so it stays until the
// plan is updated.
export async function middleware(request: NextRequest) {
  const session = await checkSession(request.cookies.get(SESSION_COOKIE)?.value, apiBase);
  const target = signInRedirect(request.nextUrl.pathname, session);
  const response = target
    ? NextResponse.redirect(new URL(target, request.url))
    : NextResponse.next();
  // A cookie the server rejected is dead weight; drop it so later requests skip the round trip.
  if (session === "invalid") response.cookies.delete(SESSION_COOKIE);
  return response;
}

export const config = {
  // Pages only: not the api (it answers 401 itself) nor assets the browser needs before it can
  // sign in (PWA files, icons, avatars).
  matcher: [
    "/((?!api/|_next/static|_next/image|manifest.webmanifest|sw.js|icon.svg|favicon.ico|icons/|avatars/).*)",
  ],
};
