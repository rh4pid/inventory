"use client";
import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, TrendingUp } from "lucide-react";

/** One loop of the scene (keep in step with the ls-* timings in globals.css). */
const CYCLE_MS = 5000;
const PRICES = [8, 20, 12, 120, 9.5, 48];
const START_SALES = 1284.5;

const money = (n: number) => "GHS " + n.toLocaleString("en-GB", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * The sign-in scene: stock flowing through a shop. A box leaves the shelf, rides the conveyor,
 * is scanned at the till, a receipt prints and it drops into the bag; the cards around it show
 * the result (sales ticking up, a low-stock alert, the books balancing). Loops every 5 s.
 * Drawn for the blue panel; the animation lives in globals.css (ls-* classes) and stops for
 * people who ask their system for less motion.
 */
export function StoreScene() {
  const [sale, setSale] = useState(0);
  const [sales, setSales] = useState(START_SALES);

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    // The till rings up as the box passes the scanner (about 62% into each loop).
    let i = 0;
    const ring = () => {
      const price = PRICES[i % PRICES.length];
      setSale(price);
      setSales((s) => s + price);
      i++;
    };
    const first = setTimeout(() => { ring(); }, CYCLE_MS * 0.62);
    const every = setInterval(ring, CYCLE_MS);
    return () => { clearTimeout(first); clearInterval(every); };
  }, []);

  return (
    <div className="relative mx-auto w-full max-w-[560px]" aria-hidden="true">
      <svg viewBox="0 0 560 400" className="block w-full">
        {/* Floor */}
        <ellipse cx="280" cy="352" rx="260" ry="18" fill="#1c3fae" opacity="0.55" />

        {/* Shelf with stock */}
        <g>
          <rect x="34" y="120" width="8" height="228" rx="3" fill="#a9c0f8" />
          <rect x="168" y="120" width="8" height="228" rx="3" fill="#a9c0f8" />
          {[176, 250, 324].map((y) => <rect key={y} x="30" y={y} width="150" height="7" rx="3" fill="#c9d8fb" />)}
          {/* [x, board top, width, height]: each box sits on its board */}
          {[[48, 176, 36, 30], [90, 176, 32, 24], [128, 176, 36, 32], [50, 250, 34, 28], [92, 250, 36, 32], [134, 250, 30, 24], [52, 324, 36, 30], [96, 324, 32, 26]].map(([x, board, w, h]) => (
            <Box key={`${x}-${board}`} x={x} y={board - h} w={w} h={h} />
          ))}
        </g>

        {/* Conveyor */}
        <g>
          <rect x="150" y="300" width="250" height="20" rx="10" fill="#14307f" />
          <rect x="150" y="300" width="250" height="5" rx="2.5" fill="#2a4fc2" />
          {[166, 202, 238, 274, 310, 346, 382].map((x) => (
            <g key={x} className="ls-roller" style={{ transformOrigin: `${x}px 310px` }}>
              <circle cx={x} cy="310" r="6" fill="#9db6f5" />
              <rect x={x - 1} y="304" width="2" height="12" fill="#14307f" />
            </g>
          ))}
          <rect x="196" y="320" width="10" height="30" rx="3" fill="#2a4fc2" />
          <rect x="344" y="320" width="10" height="30" rx="3" fill="#2a4fc2" />
        </g>

        {/* Scanner arch */}
        <g>
          <rect x="300" y="212" width="8" height="90" rx="3" fill="#c9d8fb" />
          <rect x="290" y="204" width="44" height="16" rx="6" fill="#e8eefd" />
          <rect className="ls-beam" x="309" y="220" width="6" height="80" rx="3" fill="#7dd3fc" />
        </g>

        {/* Counter + till + bag */}
        <g>
          <rect x="404" y="282" width="128" height="68" rx="8" fill="#e8eefd" />
          <rect x="404" y="282" width="128" height="10" rx="5" fill="#ffffff" />
          <rect x="418" y="214" width="62" height="54" rx="8" fill="#1c2541" />
          <rect x="424" y="220" width="50" height="34" rx="4" fill="#0f1a3d" />
          <rect x="440" y="268" width="18" height="14" rx="3" fill="#1c2541" />
          <g className="ls-receipt" style={{ transformOrigin: "449px 268px" }}>
            <rect x="436" y="268" width="26" height="30" rx="2" fill="#ffffff" />
            <rect x="440" y="275" width="18" height="2.5" rx="1" fill="#c9d8fb" />
            <rect x="440" y="281" width="12" height="2.5" rx="1" fill="#c9d8fb" />
            <rect x="440" y="287" width="16" height="2.5" rx="1" fill="#c9d8fb" />
          </g>
          {/* Shopping bag */}
          <path d="M494 262 h30 l4 42 h-38 z" fill="#f2622e" />
          <path d="M501 262 v-6 a8 8 0 0 1 16 0 v6" fill="none" stroke="#f2622e" strokeWidth="3" />
        </g>

        {/* The box on its trip */}
        <g className="ls-trip">
          <g className="ls-tripbox">
            <Box x={-17} y={-30} w={34} h={30} />
          </g>
        </g>

        {/* Till screen: the sale that just rang up */}
        <g className="ls-screen">
          <text x="449" y="234" textAnchor="middle" fontSize="8" fill="#7dd3fc" fontFamily="inherit">SOLD</text>
          <text x="449" y="248" textAnchor="middle" fontSize="11" fontWeight="700" fill="#ffffff" fontFamily="inherit">
            {sale ? `+${sale.toFixed(2)}` : "+0.00"}
          </text>
        </g>
      </svg>

      {/* Floating cards: what the scene adds up to */}
      <div className="ls-float ls-f1 absolute right-0 top-0 w-[9.5rem] rounded-xl bg-white/95 p-2.5 text-ink shadow-float sm:top-2 sm:w-[13.5rem] sm:rounded-2xl sm:p-3.5">
        <div className="flex items-center gap-1.5 text-[11px] font-medium text-ink-3 sm:text-xs"><TrendingUp className="h-3.5 w-3.5 text-brand" /> Sales today</div>
        <div className="mt-0.5 text-sm font-semibold tabular-nums tracking-tight sm:text-lg">{money(sales)}</div>
        <div className="mt-1.5 hidden h-6 items-end gap-1 sm:flex">
          {[40, 55, 35, 70, 60, 85, 100].map((h, i) => (
            <span key={i} className="ls-bar flex-1 rounded-sm bg-brand/80" style={{ height: `${h}%`, animationDelay: `${0.4 + i * 0.08}s` }} />
          ))}
        </div>
      </div>

      <div className="ls-float ls-f2 absolute left-0 top-[46%] hidden w-[13rem] items-center sm:flex gap-2.5 rounded-2xl bg-white/95 p-3 text-ink shadow-float">
        <span className="relative flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-warn-soft text-warn">
          <AlertTriangle className="h-4 w-4" />
          <span className="ls-ping absolute -right-0.5 -top-0.5 h-2.5 w-2.5 rounded-full bg-accent" />
        </span>
        <span className="min-w-0">
          <span className="block text-xs font-semibold">Low stock</span>
          <span className="block truncate text-xs text-ink-3">Fresh Eggs · 2 left at Madina</span>
        </span>
      </div>

      <div className="ls-float ls-f3 absolute bottom-0 right-2 flex items-center gap-1.5 rounded-xl bg-white/95 px-2.5 py-1.5 text-ink shadow-float sm:bottom-1 sm:right-6 sm:gap-2 sm:rounded-2xl sm:px-3 sm:py-2.5">
        <CheckCircle2 className="h-3.5 w-3.5 text-ok sm:h-4 sm:w-4" />
        <span className="text-[11px] font-semibold sm:text-xs">Books balanced</span>
      </div>
    </div>
  );
}

/** A cardboard box with tape and a label; (x, y) is its top-left corner. */
function Box({ x, y, w, h }: { x: number; y: number; w: number; h: number }) {
  return (
    <g>
      <rect x={x} y={y} width={w} height={h} rx="3" fill="#f4b183" />
      <rect x={x + w / 2 - 5} y={y} width="10" height={h * 0.3} fill="#e0955f" />
      <rect x={x + w * 0.58} y={y + h * 0.56} width={w * 0.26} height={h * 0.22} rx="1.5" fill="#f9cba6" />
    </g>
  );
}
