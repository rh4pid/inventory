"use client";
import { cn } from "@/lib/utils";
import type { DashboardData } from "@/Types";
import { Panel, PanelHeader, fmtNum } from "./Panel";

const BAR_MAX = 132; // px, tallest bar

/**
 * The blue Orders card: four patterned columns, each with its value and name riding on top, as in
 * the sample. Values are always written out, so the texture is decoration, not the only cue.
 */
export function OrdersCard({ orders, periodLabel }: { orders: DashboardData["orders"]; periodLabel: string }) {
  const bars = [
    { key: "overdue", label: "Overdue", value: orders.overdue, hint: "Credit sales past their due date with money still owed", fill: "bg-warn-fill" },
    { key: "returns", label: "Returns", value: orders.returns, hint: "Sales with items returned", fill: "fill-zigzag text-white/90 [--pattern-bg:var(--brand-blue)]" },
    { key: "unpaid", label: "Unpaid", value: orders.inProgress, hint: "Sales with a balance still due, not yet overdue", fill: "fill-speckle bg-brand-deep text-white/80" },
    { key: "completed", label: "Completed", value: orders.completed, hint: "Sales fully paid", fill: "fill-fade text-white" },
  ];
  const max = Math.max(1, ...bars.map((b) => b.value));

  return (
    <Panel tone="blue" className="flex min-h-[19rem] flex-col">
      <PanelHeader title="Orders" subtitle={`${fmtNum(orders.total)} sales ${periodLabel}`} href="/sales" linkLabel="Open sales" tone="blue" />
      <div
        className="-mb-5 mt-4 grid flex-1 grid-cols-4 items-end gap-2.5 md:-mb-6"
        role="img"
        aria-label={bars.map((b) => `${b.label}: ${b.value}`).join(", ")}
      >
        {bars.map((b) => {
          const px = b.value === 0 ? 6 : Math.max(18, (b.value / max) * BAR_MAX);
          return (
            <div key={b.key} className="group flex min-w-0 flex-col justify-end" title={`${b.label}: ${b.value}. ${b.hint}`}>
              <div className="mb-2 min-w-0 px-0.5">
                <div className="text-[22px] font-semibold leading-none tabular-nums">{fmtNum(b.value)}</div>
                <div className="mt-1 truncate text-[12px] text-white/85">{b.label}</div>
              </div>
              <div
                className={cn(
                  "rounded-t-2xl transition-[height,filter] duration-500 group-hover:brightness-110",
                  b.value === 0 ? "bg-white/25" : b.fill
                )}
                style={{ height: px }}
              />
            </div>
          );
        })}
      </div>
    </Panel>
  );
}
