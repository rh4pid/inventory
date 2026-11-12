"use client";
import { useId, useState } from "react";
import type { DashboardData } from "@/Types";
import { Panel, PanelHeader, fmtNum } from "./Panel";

const NAVY = "#1c2541";
const ORANGE = "#f2622e";

/**
 * The orange Stock card: a legend on the left and a patterned half-donut on the right.
 * Every product sits in exactly one bucket (out > low > dead > in stock).
 */
export function StockCard({ stock }: { stock: DashboardData["stock"] }) {
  const uid = useId().replace(/:/g, "");
  const [hover, setHover] = useState<string | null>(null);

  const segments = [
    { key: "in", label: "In stock", value: stock.inStock, fill: NAVY, hint: "Selling and above the reorder level" },
    { key: "low", label: "Low stock", value: stock.lowStock, fill: `url(#${uid}-stripes)`, hint: "At or below the reorder level" },
    { key: "out", label: "Out of stock", value: stock.outOfStock, fill: "#ffffff", hint: "None left here" },
    { key: "dead", label: "Dead stock", value: stock.deadStock, fill: `url(#${uid}-dots)`, hint: "No sales and no new stock for 90 days" },
  ];
  const total = segments.reduce((s, x) => s + x.value, 0);

  // Half donut: centre at the bottom middle, sweeping from the left (180°) to the right (0°).
  const W = 220, H = 120, cx = 110, cy = 116, R = 104, r = 58, gapDeg = total > 0 ? 1.4 : 0;
  const pt = (rad: number, deg: number) => [cx + rad * Math.cos((deg * Math.PI) / 180), cy - rad * Math.sin((deg * Math.PI) / 180)];
  let angle = 180;
  const arcs = segments.map((s) => {
    const sweep = total > 0 ? (s.value / total) * 180 : 0;
    const a0 = angle, a1 = angle - sweep;
    angle = a1;
    if (sweep <= gapDeg) return { ...s, d: null as string | null };
    const s0 = a0 - gapDeg / 2, s1 = a1 + gapDeg / 2;
    const [x0, y0] = pt(R, s0), [x1, y1] = pt(R, s1), [x2, y2] = pt(r, s1), [x3, y3] = pt(r, s0);
    const large = s0 - s1 > 180 ? 1 : 0;
    return { ...s, d: `M${x0} ${y0} A${R} ${R} 0 ${large} 1 ${x1} ${y1} L${x2} ${y2} A${r} ${r} 0 ${large} 0 ${x3} ${y3}Z` };
  });
  const focus = segments.find((s) => s.key === hover);

  return (
    <Panel tone="orange" className="flex min-h-[19rem] flex-col">
      <PanelHeader title="Stock" subtitle={`${fmtNum(stock.products)} products tracked`} href="/inventory" linkLabel="Open inventory" tone="orange" />
      <div className="mt-4 flex flex-1 flex-col justify-end gap-4 sm:flex-row sm:items-end sm:justify-between">
        <ul className="min-w-[10.5rem] shrink-0 space-y-2.5 whitespace-nowrap text-[13px]">
          {segments.map((s) => (
            <li
              key={s.key}
              className="flex items-center gap-2.5"
              onMouseEnter={() => setHover(s.key)}
              onMouseLeave={() => setHover(null)}
              title={s.hint}
            >
              <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
                <circle cx="8" cy="8" r="7" fill={s.fill} stroke={NAVY} strokeOpacity={s.key === "out" ? 0.25 : 0} />
              </svg>
              <span className="font-medium">{s.label}</span>
              <span className="ml-auto pl-3 font-semibold tabular-nums">{fmtNum(s.value)}</span>
            </li>
          ))}
        </ul>

        <div className="relative -mb-5 -mr-5 w-full max-w-[15rem] self-end sm:min-w-[11rem] md:-mb-6 md:-mr-6">
          <svg viewBox={`0 0 ${W} ${H}`} className="block w-full" role="img"
            aria-label={`Stock health: ${segments.map((s) => `${s.label} ${s.value}`).join(", ")}`}>
            <defs>
              <pattern id={`${uid}-stripes`} width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
                <rect width="7" height="7" fill={ORANGE} />
                <rect width="2.6" height="7" fill={NAVY} />
              </pattern>
              <pattern id={`${uid}-dots`} width="6" height="6" patternUnits="userSpaceOnUse">
                <rect width="6" height="6" fill="#ffffff" />
                <circle cx="3" cy="3" r="1.3" fill={NAVY} />
              </pattern>
            </defs>
            {total === 0 && (
              <path d={`M${cx - R} ${cy} A${R} ${R} 0 0 1 ${cx + R} ${cy} L${cx + r} ${cy} A${r} ${r} 0 0 0 ${cx - r} ${cy}Z`} fill="#ffffff" fillOpacity={0.35} />
            )}
            {arcs.map((a) => a.d && (
              <path
                key={a.key}
                d={a.d}
                fill={a.fill}
                stroke={ORANGE}
                strokeWidth={0.5}
                opacity={hover && hover !== a.key ? 0.45 : 1}
                onMouseEnter={() => setHover(a.key)}
                onMouseLeave={() => setHover(null)}
                className="transition-opacity"
              >
                <title>{`${a.label}: ${a.value}`}</title>
              </path>
            ))}
          </svg>
          <div className="pointer-events-none absolute inset-x-0 bottom-[4%] text-center">
            <div className="text-xl font-semibold leading-none tabular-nums">{fmtNum(focus ? focus.value : total)}</div>
            <div className="text-[11px] font-medium text-ink/75">{focus ? focus.label : "products"}</div>
          </div>
        </div>
      </div>
    </Panel>
  );
}
