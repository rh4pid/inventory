"use client";
import { useMemo, useState } from "react";
import Link from "next/link";
import { cn } from "@/lib/utils";
import type { DashboardData } from "@/Types";
import { Panel, PanelHeader, Monogram, fmtCompact, fmtNum } from "./Panel";

type Day = DashboardData["flow"][number];
interface Bucket { label: string; long: string; in: number; out: number }

const d = (iso: string) => new Date(iso + "T00:00:00");

/** Days → buckets: daily up to a month, weekly for a quarter, monthly for a year. */
function bucketize(days: Day[]): { buckets: Bucket[]; unit: string } {
  if (days.length <= 31) {
    return {
      unit: "day",
      buckets: days.map((x) => ({
        label: d(x.date).toLocaleDateString(undefined, { day: "numeric", month: "short" }),
        long: d(x.date).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" }),
        in: x.in, out: x.out,
      })),
    };
  }
  const byWeek = days.length <= 120;
  const map = new Map<string, Bucket>();
  for (const x of days) {
    const date = d(x.date);
    let key: string, label: string, long: string;
    if (byWeek) {
      const start = new Date(date);
      start.setDate(date.getDate() - ((date.getDay() + 6) % 7)); // Monday
      key = start.toISOString().slice(0, 10);
      label = start.toLocaleDateString(undefined, { day: "numeric", month: "short" });
      long = `Week of ${label}`;
    } else {
      key = `${date.getFullYear()}-${date.getMonth()}`;
      label = date.toLocaleDateString(undefined, { month: "short" });
      long = date.toLocaleDateString(undefined, { month: "long", year: "numeric" });
    }
    const b = map.get(key) ?? { label, long, in: 0, out: 0 };
    b.in += x.in; b.out += x.out;
    map.set(key, b);
  }
  return { unit: byWeek ? "week" : "month", buckets: [...map.values()] };
}

function niceMax(v: number) {
  if (v <= 0) return 4;
  const p = Math.pow(10, Math.floor(Math.log10(v)));
  const n = v / p;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 4 ? 4 : n <= 5 ? 5 : 10) * p;
}

const REASON: Record<DashboardData["attention"][number]["reason"], string> = {
  OUT: "Out of stock", LOW: "Low stock", EXPIRED: "Expired", EXPIRING: "Expiring soon",
};

/**
 * Takes the place of the sample's logistics map: units coming in vs going out over the period,
 * with the products that need attention stacked bottom-left.
 */
export function StockFlowCard({ flow, attention, attentionTotal }: {
  flow: Day[];
  attention: DashboardData["attention"];
  attentionTotal: number;
}) {
  const { buckets, unit } = useMemo(() => bucketize(flow), [flow]);
  const [hover, setHover] = useState<number | null>(null);
  const totals = buckets.reduce((s, b) => ({ in: s.in + b.in, out: s.out + b.out }), { in: 0, out: 0 });
  const max = niceMax(Math.max(0, ...buckets.map((b) => Math.max(b.in, b.out))));
  const ticks = [max, max / 2, 0];
  const labelEvery = Math.max(1, Math.ceil(buckets.length / 7));
  // One chip per product (a product can need attention for several batches).
  const uniqueAttention = attention.filter((a, i) => attention.findIndex((b) => b.productId === a.productId) === i);
  const shown = uniqueAttention.slice(0, 4);
  const h = hover != null ? buckets[hover] : null;

  return (
    <Panel className="flex min-h-[19rem] flex-col">
      <div className="flex flex-1 flex-col gap-5 lg:flex-row">
        {/* Left: title, totals, needs attention */}
        <div className="flex shrink-0 flex-col lg:w-56">
          <PanelHeader title="Stock flow" subtitle={`Units in and out per ${unit}`} href="/inventory" linkLabel="Open stock movements" />
          <dl className="mt-4 grid grid-cols-2 gap-3 lg:grid-cols-1">
            <div className="rounded-2xl bg-surface-2 px-3.5 py-2.5">
              <dt className="flex items-center gap-2 text-xs text-ink-3"><span className="h-2.5 w-2.5 rounded-full bg-brand" />Stock in</dt>
              <dd className="text-xl font-semibold text-ink tabular-nums">{fmtNum(totals.in)}</dd>
            </div>
            <div className="rounded-2xl bg-surface-2 px-3.5 py-2.5">
              <dt className="flex items-center gap-2 text-xs text-ink-3"><span className="h-2.5 w-2.5 rounded-full bg-accent" />Stock out</dt>
              <dd className="text-xl font-semibold text-ink tabular-nums">{fmtNum(totals.out)}</dd>
            </div>
          </dl>

          <div className="mt-auto pt-5">
            <p className="mb-2 text-[13px] font-medium text-ink">
              {attentionTotal > 0 ? "Needs attention" : "Nothing needs attention"}
            </p>
            {attentionTotal > 0 && (
              <Link href="/inventory" className="inline-flex items-center rounded-2xl border border-line bg-surface p-1 pr-3 shadow-card hover:border-accent/40">
                <span className="flex -space-x-2">
                  {shown.map((a, k) => (
                    <span key={`${a.productId}-${k}`} title={`${a.productName}: ${REASON[a.reason]} (${a.detail})`}>
                      <Monogram name={a.productName} className="h-9 w-9 rounded-xl ring-2 ring-surface" />
                    </span>
                  ))}
                </span>
                {attentionTotal > shown.length && (
                  <span className="ml-2 text-sm font-semibold text-ink">+{attentionTotal - shown.length}</span>
                )}
                {attentionTotal <= shown.length && (
                  <span className="ml-2 text-xs text-ink-3">{attentionTotal} item{attentionTotal === 1 ? "" : "s"}</span>
                )}
              </Link>
            )}
          </div>
        </div>

        {/* Right: paired columns */}
        <div className="relative flex min-h-[13rem] min-w-0 flex-1 flex-col">
          <div className="relative flex flex-1">
            {/* y axis + gridlines */}
            <div className="flex w-9 shrink-0 flex-col justify-between pb-6 text-right text-[11px] text-ink-3 tabular-nums" aria-hidden="true">
              {ticks.map((t) => <span key={t} className="-translate-y-1/2 pr-2 leading-none">{fmtCompact(t)}</span>)}
            </div>
            <div className="relative flex min-w-0 flex-1 flex-col">
              <div className="pointer-events-none absolute inset-x-0 top-0 bottom-6 flex flex-col justify-between" aria-hidden="true">
                {ticks.map((t) => <div key={t} className="h-px bg-line" />)}
              </div>
              <div
                className="relative flex flex-1 items-end gap-[2px] pb-6"
                role="img"
                aria-label={`Stock in ${fmtNum(totals.in)} and stock out ${fmtNum(totals.out)} units over ${buckets.length} ${unit}s`}
                onMouseLeave={() => setHover(null)}
              >
                {buckets.map((b, k) => (
                  <div
                    key={k}
                    className="relative flex h-full min-w-0 flex-1 cursor-default flex-col justify-end"
                    onMouseEnter={() => setHover(k)}
                  >
                    <div className={cn("absolute inset-x-0 top-0 bottom-0 rounded-lg transition-colors", hover === k && "bg-surface-2")} aria-hidden="true" />
                    <div className="relative flex h-full items-end justify-center gap-[2px]">
                      <div className="w-full max-w-[10px] rounded-t-[4px] bg-brand" style={{ height: `${(b.in / max) * 100}%` }} />
                      <div className="w-full max-w-[10px] rounded-t-[4px] bg-accent" style={{ height: `${(b.out / max) * 100}%` }} />
                    </div>
                    {k % labelEvery === 0 && (
                      <span className="absolute -bottom-6 left-1/2 -translate-x-1/2 whitespace-nowrap text-[11px] text-ink-3" aria-hidden="true">
                        {b.label}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Legend */}
          <div className="mt-2 flex items-center justify-end gap-4 text-xs text-ink-2">
            <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm bg-brand" />In</span>
            <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm bg-accent" />Out</span>
          </div>

          {/* Tooltip */}
          {h && (
            <div className="pointer-events-none absolute left-12 top-0 z-10 rounded-xl border border-line bg-surface px-3 py-2 text-xs shadow-float">
              <div className="font-semibold text-ink">{h.long}</div>
              <div className="mt-1 flex items-center gap-2 text-ink-2"><span className="h-2 w-2 rounded-full bg-brand" />In <b className="ml-auto pl-4 text-ink tabular-nums">{fmtNum(h.in)}</b></div>
              <div className="flex items-center gap-2 text-ink-2"><span className="h-2 w-2 rounded-full bg-accent" />Out <b className="ml-auto pl-4 text-ink tabular-nums">{fmtNum(h.out)}</b></div>
            </div>
          )}
        </div>
      </div>
    </Panel>
  );
}
