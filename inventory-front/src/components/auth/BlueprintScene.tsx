"use client";
import { useEffect, useState } from "react";
import { APP_TAGLINE } from "@/lib/brand";

/**
 * The sign-in scene: a stock item drawn as a blueprint. A shipping box draws itself (outline,
 * edges, tape, label, barcode), dimension lines extend with their measurements, the top and side
 * views draw in, and the title block fills in with live-looking stock figures. Afterwards a
 * scanner line sweeps the drawing and the quantity on hand moves (sales, then a delivery when it
 * runs low). Animation: bp-* classes in globals.css; every drawn line uses pathLength=1.
 */

const START_QTY = 240;
const REORDER_AT = 228;

type StockEvent = { id: number; text: string; tone: "out" | "in" };

function useStockTicker() {
  // null until the page has loaded (shown as "—"), then counts up.
  const [qty, setQty] = useState<number | null>(null);
  const [event, setEvent] = useState<StockEvent | null>(null);

  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce) {
      // No counting animation: show the figure straight away.
      const t = setTimeout(() => setQty(START_QTY), 0);
      return () => clearTimeout(t);
    }
    let raf = 0;
    let n = 0;
    let timer: ReturnType<typeof setInterval> | undefined;
    // Count up once the title block has drawn in (~3.4 s).
    const startCount = setTimeout(() => {
      const t0 = performance.now();
      const step = (now: number) => {
        const p = Math.min(1, (now - t0) / 1100);
        setQty(Math.round(START_QTY * (1 - Math.pow(1 - p, 3))));
        if (p < 1) raf = requestAnimationFrame(step);
      };
      raf = requestAnimationFrame(step);
      // Then stock moves: a few sales, and a delivery when it gets low.
      timer = setInterval(() => {
        n++;
        setQty((prev) => {
          const q = prev ?? START_QTY;
          if (q <= REORDER_AT) {
            setEvent({ id: n, text: "+24 received", tone: "in" });
            return q + 24;
          }
          const sold = 1 + (n % 3);
          setEvent({ id: n, text: `−${sold} sold`, tone: "out" });
          return q - sold;
        });
      }, 4200);
    }, 3400);
    return () => { clearTimeout(startCount); cancelAnimationFrame(raf); if (timer) clearInterval(timer); };
  }, []);

  return { qty, event };
}

const line = { fill: "none", stroke: "currentColor", strokeLinecap: "round" as const, strokeLinejoin: "round" as const };
const at = (s: number) => ({ animationDelay: `${s}s` });

export function BlueprintScene() {
  const { qty, event } = useStockTicker();

  return (
    <div className="relative" aria-hidden="true">
      <div className="grid items-center gap-4 sm:grid-cols-[minmax(0,1fr)_9.5rem] lg:grid-cols-[minmax(0,1fr)_10.5rem]">
        {/* ── Main drawing ───────────────────────────────────────────── */}
        <div className="relative">
          <svg viewBox="0 0 400 370" className="mx-auto block h-auto max-h-[240px] w-full text-white sm:max-h-[44vh]">
            <defs>
              <marker id="bp-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
                <path d="M0 1 L9 5 L0 9" fill="none" stroke="#ffffff" strokeWidth="1.4" />
              </marker>
              <linearGradient id="bp-scan" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stopColor="#bfe3ff" stopOpacity="0" />
                <stop offset="0.5" stopColor="#bfe3ff" stopOpacity="0.55" />
                <stop offset="1" stopColor="#bfe3ff" stopOpacity="0" />
              </linearGradient>
            </defs>

            {/* centre lines */}
            <g className="bp-fade" style={at(0.2)} opacity="0.35">
              <path d="M217 34 V352" {...line} strokeWidth="0.8" strokeDasharray="14 4 2 4" />
              <path d="M70 212 H366" {...line} strokeWidth="0.8" strokeDasharray="14 4 2 4" />
            </g>

            {/* silhouette, then inner edges */}
            <path className="bp-draw" style={{ ...at(0.3), animationDuration: "1.5s" }} pathLength={1}
              d="M200 80 L329.9 155 L329.9 275 L234.6 330 L104.7 255 L104.7 135 Z" {...line} strokeWidth="2.2" />
            <path className="bp-draw" style={at(1.3)} pathLength={1}
              d="M104.7 135 L234.6 210 L329.9 155 M234.6 210 V330" {...line} strokeWidth="1.8" />

            {/* top flaps seam and tape running over the top and down the right side */}
            <path className="bp-draw" style={at(1.7)} pathLength={1} d="M152.4 107.5 L282.3 182.5" {...line} strokeWidth="1" opacity="0.7" />
            <path className="bp-draw" style={at(1.9)} pathLength={1} d="M145.7 111.4 L275.6 186.4 V306.4" {...line} strokeWidth="1.3" />
            <path className="bp-draw" style={at(2.0)} pathLength={1} d="M159.1 103.6 L289 178.6 V298.6" {...line} strokeWidth="1.3" />

            {/* shipping label with barcode on the left face */}
            <path className="bp-draw" style={at(2.2)} pathLength={1} d="M124.2 172 L182.7 205.8 V249.8 L124.2 216 Z" {...line} strokeWidth="1.3" />
            <g>
              {[0.08, 0.15, 0.2, 0.3, 0.36, 0.44, 0.5, 0.6, 0.66, 0.74, 0.82, 0.9].map((t, i) => {
                const x = 124.2 + 58.5 * t, y = 172 + 33.75 * t;
                return <path key={t} className="bp-bar" style={at(2.4 + i * 0.04)} d={`M${x} ${y + 8} V${y + 26}`} {...line} strokeWidth={i % 3 === 0 ? 2.2 : 1} />;
              })}
            </g>

            {/* "this side up" arrows on the right face */}
            <g className="bp-fade" style={at(2.5)}>
              <path d="M303 236 V214 M297.5 220 L303 214 L308.5 220 M315 229 V207 M309.5 213 L315 207 L320.5 213" {...line} strokeWidth="1.4" />
            </g>

            {/* dimension lines */}
            <g className="bp-fade" style={at(2.7)}>
              <path d="M98 135 H70 M98 255 H70" {...line} strokeWidth="0.8" opacity="0.7" />
              <path className="bp-draw" style={at(2.7)} pathLength={1} d="M78 140 V250" {...line} strokeWidth="1" markerStart="url(#bp-arrow)" markerEnd="url(#bp-arrow)" />
              <text x="60" y="199" textAnchor="middle" className="bp-label" transform="rotate(-90 60 195)">250 mm</text>
            </g>
            <g className="bp-fade" style={at(2.9)}>
              <path d="M241 334 L250 349.6 M336 279 L345 294.6" {...line} strokeWidth="0.8" opacity="0.7" />
              <path className="bp-draw" style={at(2.9)} pathLength={1} d="M250.5 343.5 L335.5 294.5" {...line} strokeWidth="1" markerStart="url(#bp-arrow)" markerEnd="url(#bp-arrow)" />
              <text x="306" y="337" textAnchor="middle" className="bp-label" transform="rotate(-30 306 333)">400 mm</text>
            </g>
            <g className="bp-fade" style={at(3.1)}>
              <path d="M98 259 L89 274.6 M228 334 L219 349.6" {...line} strokeWidth="0.8" opacity="0.7" />
              <path className="bp-draw" style={at(3.1)} pathLength={1} d="M97 268.5 L219.5 339.5" {...line} strokeWidth="1" markerStart="url(#bp-arrow)" markerEnd="url(#bp-arrow)" />
              <text x="146" y="324" textAnchor="middle" className="bp-label" transform="rotate(30 146 320)">300 mm</text>
            </g>

            {/* call-out to the label */}
            <g className="bp-fade" style={at(3.3)}>
              <circle cx="152" cy="200" r="2.6" fill="#ffffff" />
              <path d="M152 200 L96 72 H40" {...line} strokeWidth="0.9" />
              <text x="40" y="64" className="bp-label">SKU BX-0001</text>
            </g>

            {/* scanner sweep (after the drawing is done) */}
            <rect className="bp-scan" x="60" y="40" width="300" height="34" fill="url(#bp-scan)" />
          </svg>

          {/* phones: the one figure that matters */}
          <div className="bp-fade absolute right-0 top-0 border border-white/60 px-2.5 py-1.5 font-mono text-[10px] uppercase tracking-wider lg:hidden" style={at(3.4)}>
            On hand <b className="ml-1 text-sm tabular-nums">{qty ?? "—"}</b>
          </div>
        </div>

        {/* ── Orthographic views (tablets and up) ─────────────────────── */}
        <div className="hidden flex-col gap-3 sm:flex">
          <View title="Top view" delay={3.3}>
            <rect className="bp-draw" style={at(3.4)} pathLength={1} x="20" y="34" width="110" height="74" {...line} strokeWidth="1.4" />
            <path className="bp-draw" style={at(3.7)} pathLength={1} d="M20 66 H130 M20 76 H130" {...line} strokeWidth="1" />
            <path className="bp-fade" style={at(3.8)} d="M75 26 V116" {...line} strokeWidth="0.7" strokeDasharray="10 3 2 3" opacity="0.5" />
            <path className="bp-fade" style={at(3.9)} d="M24 124 H126" {...line} strokeWidth="0.8" markerStart="url(#bp-arrow)" markerEnd="url(#bp-arrow)" />
            <text className="bp-fade bp-label" style={at(4)} x="75" y="140" textAnchor="middle">400</text>
          </View>
          <View title="Side view" delay={3.6}>
            <rect className="bp-draw" style={at(3.7)} pathLength={1} x="30" y="34" width="90" height="80" {...line} strokeWidth="1.4" />
            <path className="bp-draw" style={at(4)} pathLength={1} d="M70 34 V114 M80 34 V114" {...line} strokeWidth="1" />
            <rect className="bp-draw" style={at(4.1)} pathLength={1} x="38" y="62" width="24" height="18" {...line} strokeWidth="0.9" />
            <path className="bp-fade" style={at(4.2)} d="M18 38 V110" {...line} strokeWidth="0.8" markerStart="url(#bp-arrow)" markerEnd="url(#bp-arrow)" />
            <text className="bp-fade bp-label" style={at(4.3)} x="10" y="78" textAnchor="middle" transform="rotate(-90 10 74)">250</text>
          </View>
        </div>
      </div>

      {/* ── Title block (laptops and up) ──────────────────────────────── */}
      <dl className="bp-fade mt-4 hidden grid-cols-[6.5rem_minmax(0,1fr)_6.5rem_minmax(0,1fr)] border border-white/45 font-mono text-[11px] uppercase tracking-wider lg:grid" style={at(3.5)}>
        <Cell label="Project" wide>Stock item · Shipping carton</Cell>
        <Cell label="SKU">BX-0001</Cell>
        <Cell label="Location" end>Warehouse A · Bay 3</Cell>
        <Cell label="On hand">
          <span className="inline-flex items-center gap-2">
            <b className="text-[13px] tabular-nums">{qty ?? "—"}</b>
            {event && (
              <span key={event.id} className={`bp-chip rounded px-1.5 py-0.5 text-[10px] font-semibold ${event.tone === "in" ? "bg-emerald-300/25 text-emerald-100" : "bg-white/15 text-white"}`}>
                {event.text}
              </span>
            )}
          </span>
        </Cell>
        <Cell label="Reorder at" end>200</Cell>
        <Cell label="Notes" wide last>{APP_TAGLINE}</Cell>
      </dl>
    </div>
  );
}

function View({ title, delay, children }: { title: string; delay: number; children: React.ReactNode }) {
  return (
    <svg viewBox="0 0 150 150" className="block h-auto w-full text-white">
      <rect className="bp-draw" style={at(delay)} pathLength={1} x="1" y="1" width="148" height="148" {...line} strokeWidth="1" opacity="0.6" />
      <text className="bp-fade bp-label" style={at(delay + 0.1)} x="75" y="18" textAnchor="middle">{title.toUpperCase()}</text>
      {children}
    </svg>
  );
}

/** One label + value pair of the title block. `wide` spans the row; `end` is the last pair in its row. */
function Cell({ label, children, wide, end, last }: { label: string; children: React.ReactNode; wide?: boolean; end?: boolean; last?: boolean }) {
  const bottom = last ? "" : "border-b border-white/45";
  const right = wide || end ? "" : "border-r border-white/45";
  return (
    <>
      <dt className={`border-r border-white/45 px-2.5 py-1.5 text-white/70 ${bottom}`}>{label}</dt>
      <dd className={`min-w-0 truncate px-2.5 py-1.5 ${bottom} ${right} ${wide ? "col-span-3" : ""}`}>{children}</dd>
    </>
  );
}
