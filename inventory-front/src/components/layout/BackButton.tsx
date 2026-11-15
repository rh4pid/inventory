"use client";
import { useEffect, useRef } from "react";
import { usePathname, useRouter } from "next/navigation";
import { ArrowLeft } from "lucide-react";

/** "/products/12/edit" -> "/products"; "/inventory/counts/4" -> "/inventory/counts"; top level -> "/overview". */
function parentOf(path: string) {
  const parts = path.split("/").filter(Boolean);
  if (parts.length <= 1) return "/overview";
  // Drop a trailing action ("edit", "new") together with the id before it.
  const last = parts[parts.length - 1];
  const cut = (last === "edit" && parts.length > 2) ? 2 : 1;
  return "/" + parts.slice(0, parts.length - cut).join("/");
}

/**
 * Back to the page you came from inside the app; if you opened this page directly (bookmark,
 * refresh), up one level instead. Hidden on the overview (home).
 */
export function BackButton() {
  const pathname = usePathname();
  const router = useRouter();
  // Pages visited in this tab, oldest first. Lives as long as the app frame (the dashboard layout).
  const trail = useRef<string[]>([]);
  const goingBack = useRef(false);

  useEffect(() => {
    const t = trail.current;
    if (goingBack.current) {
      goingBack.current = false;
      // We went back: drop everything after the page we returned to.
      const i = t.lastIndexOf(pathname);
      trail.current = i >= 0 ? t.slice(0, i + 1) : [pathname];
    } else if (t[t.length - 1] !== pathname) {
      t.push(pathname);
      if (t.length > 50) t.shift();
    }
  }, [pathname]);

  if (pathname === "/overview") return null;

  const back = () => {
    const t = trail.current;
    const previous = t.length > 1 ? t[t.length - 2] : null;
    if (previous) {
      goingBack.current = true;
      router.back();
    } else {
      router.push(parentOf(pathname));
    }
  };

  return (
    <button
      type="button"
      onClick={back}
      className="mb-3 inline-flex h-9 items-center gap-1.5 rounded-xl border border-line bg-surface px-3 text-sm font-medium text-ink-2 shadow-card hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand"
    >
      <ArrowLeft className="h-4 w-4" /> Back
    </button>
  );
}
