import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const PUBLIC = ["/login", "/register"];
const REFRESH_COOKIE = "_ultimate_";

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const hasCookie = !!request.cookies.get(REFRESH_COOKIE);

  if (PUBLIC.some((p) => pathname.startsWith(p))) {
    if (hasCookie) {
      return NextResponse.redirect(new URL("/overview", request.url));

    }
    return NextResponse.next();
  }

  if (
    pathname === "/" ||
    pathname.startsWith("/overview") ||
    pathname.startsWith("/products") ||
    pathname.startsWith("/categories") ||
    pathname.startsWith("/inventory") ||
    pathname.startsWith("/sales") ||
    pathname.startsWith("/purchases") ||
    pathname.startsWith("/customers") ||
    pathname.startsWith("/suppliers") ||
    pathname.startsWith("/settings")
  ) {
    if (!hasCookie) {
      return NextResponse.redirect(new URL("/login", request.url));
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
