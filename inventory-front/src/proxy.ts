import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const PUBLIC = ["/login", "/register"];
const REFRESH_COOKIE = "_ultimate_";

/**
 * Coarse guard on the session cookie; the backend checks every request properly.
 * The landing page (/) and /login, /register are public; anyone can create an account.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const hasCookie = !!request.cookies.get(REFRESH_COOKIE);

  // The landing page: visitors see it; signed-in people go straight to the app.
  if (pathname === "/") {
    return hasCookie ? NextResponse.redirect(new URL("/overview", request.url)) : NextResponse.next();
  }

  if (PUBLIC.some((p) => pathname.startsWith(p))) {
    // Signed in already: into the app (the super admin is sent on to the admin console from there).
    return hasCookie ? NextResponse.redirect(new URL("/overview", request.url)) : NextResponse.next();
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
