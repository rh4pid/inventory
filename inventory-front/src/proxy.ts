import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const PUBLIC = ["/login"];
const REFRESH_COOKIE = "_ultimate_";

/**
 * Coarse guard on the session cookie; the backend checks every request properly.
 * Public sign-up is closed: companies are created by the super admin, people by their admin.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const hasCookie = !!request.cookies.get(REFRESH_COOKIE);

  if (pathname.startsWith("/register")) {
    return NextResponse.redirect(new URL("/login", request.url));
  }

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
