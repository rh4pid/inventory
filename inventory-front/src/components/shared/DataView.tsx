"use client";
import { useCallback, useLayoutEffect, useRef, useSyncExternalStore } from "react";
import { LayoutGrid, Rows3 } from "lucide-react";
import { cn } from "@/lib/utils";

export type ViewMode = "table" | "grid";

const storageKey = (id: string) => `view:${id}`;

function readMode(id: string, fallback: ViewMode): ViewMode {
  try {
    const v = localStorage.getItem(storageKey(id));
    return v === "grid" || v === "table" ? v : fallback;
  } catch { return fallback; }
}

const CHANGE = "viewmode-change";

function subscribe(onChange: () => void) {
  window.addEventListener(CHANGE, onChange);
  window.addEventListener("storage", onChange); // other tabs
  return () => { window.removeEventListener(CHANGE, onChange); window.removeEventListener("storage", onChange); };
}

/** Table or grid, remembered per list (in this browser). */
export function useViewMode(id: string, fallback: ViewMode = "table") {
  const mode = useSyncExternalStore(subscribe, () => readMode(id, fallback), () => fallback);
  const setMode = useCallback((m: ViewMode) => {
    try { localStorage.setItem(storageKey(id), m); } catch { /* private mode: can't remember */ }
    window.dispatchEvent(new Event(CHANGE));
  }, [id]);
  return [mode, setMode] as const;
}

/** The two-button switch: table | grid. */
export function ViewToggle({ mode, onChange, className }: { mode: ViewMode; onChange: (m: ViewMode) => void; className?: string }) {
  const btn = (m: ViewMode, label: string, Icon: typeof Rows3) => (
    <button
      type="button"
      onClick={() => onChange(m)}
      aria-pressed={mode === m}
      aria-label={label}
      title={label}
      className={cn(
        "flex h-9 w-9 items-center justify-center rounded-xl transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand",
        mode === m ? "bg-surface text-ink shadow-card" : "text-ink-3 hover:text-ink"
      )}
    >
      <Icon className="h-4 w-4" />
    </button>
  );
  return (
    <div role="group" aria-label="View" className={cn("inline-flex shrink-0 gap-0.5 rounded-2xl border border-line bg-surface-2 p-0.5", className)}>
      {btn("table", "Table view", Rows3)}
      {btn("grid", "Grid view", LayoutGrid)}
    </div>
  );
}

/**
 * Wraps a list table so it can also be shown as a grid of cards. In grid mode each cell gets
 * its column's header as a label (so cards read "Price: GHS 12"), using plain CSS on the same
 * table markup; sorting, buttons and links keep working.
 *
 *   const [view, setView] = useViewMode("products");
 *   <ViewToggle mode={view} onChange={setView} />
 *   <DataView mode={view}> …<Table>…</Table>… </DataView>
 */
export function DataView({ mode, children, className }: { mode: ViewMode; children: React.ReactNode; className?: string }) {
  const ref = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const root = ref.current;
    if (!root || mode !== "grid") return;
    const label = () => {
      root.querySelectorAll("table").forEach((table) => {
        const heads = Array.from(table.querySelectorAll("thead th")).map((th) => th.textContent?.trim() ?? "");
        table.querySelectorAll("tbody tr").forEach((tr) => {
          let col = 0;
          Array.from(tr.children).forEach((td) => {
            const text = heads[col] ?? "";
            if (td.getAttribute("data-label") !== text) td.setAttribute("data-label", text);
            col += Number(td.getAttribute("colspan") ?? 1);
          });
        });
      });
    };
    label();
    // Rows change as data loads, pages turn or filters apply.
    const obs = new MutationObserver(label);
    obs.observe(root, { childList: true, subtree: true });
    return () => obs.disconnect();
  }, [mode]);

  return <div ref={ref} className={cn(mode === "grid" && "data-grid", className)}>{children}</div>;
}
