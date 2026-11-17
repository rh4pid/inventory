import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const PUBLIC = ["/login", "/register"];
/** Open whether or not you're signed in (a reset link must work from any browser). */
const ALWAYS_OPEN = ["/forgot-password", "/reset-password"];
const REFRESH_COOKIE = "_ultimate_";

/**
 * Coarse guard on the session cookie; the backend checks every request properly.
 * Anyone can create an account at /register; companies are created by their owners.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const hasCookie = !!request.cookies.get(REFRESH_COOKIE);

  if (ALWAYS_OPEN.some((p) => pathname.startsWith(p))) return NextResponse.next();

  if (PUBLIC.some((p) => pathname.startsWith(p))) {
    // Signed in already: the app decides where to go (overview, or the admin console).
    return hasCookie ? NextResponse.redirect(new URL("/", request.url)) : NextResponse.next();
  }

  if (!hasCookie) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

export const config = {
  // Everything except API calls (forwarded to the backend, which checks auth itself), Next internals and static files.
  matcher: ["/((?!api/|_next/static|_next/image|favicon.ico|.*\.(?:png|jpg|jpeg|svg|webp|ico|txt)$).*)"],
};
