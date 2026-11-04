import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { SESSION_COOKIE, signInRedirect } from "@/auth/redirect";
import { checkSession } from "@/auth/session";

const apiBase = process.env.API_BASE_URL ?? "http://localhost:8080";

// Next 16 renames this convention to proxy.ts; plan.md names middleware.ts, so it stays until the
// plan is updated.
export async function middleware(request: NextRequest) {
  const session = await checkSession(request.cookies.get(SESSION_COOKIE)?.value, apiBase);
  const target = signInRedirect(request.nextUrl.pathname, session, request.nextUrl.search);
  // Never clear the cookie on a 401: pu_session also carries the anonymous session that holds an
  // in-flight Google sign-in, which a second tab would otherwise destroy. A stale cookie is
  // replaced by the next sign-in.
  return target ? NextResponse.redirect(new URL(target, request.url)) : NextResponse.next();
}

export const config = {
  // Pages only: not the api (it answers 401 itself) nor assets the browser needs before it can
  // sign in (PWA files, icons, avatars).
  matcher: [
    "/((?!api/|_next/static|_next/image|manifest.webmanifest|sw.js|icon.svg|favicon.ico|icons/|avatars/).*)",
  ],
};
