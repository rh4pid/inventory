"use client";
import { useState } from "react";
import { ChevronLeft, ChevronRight, ShoppingBag, Truck } from "lucide-react";
import { cn, formatCurrency } from "@/lib/utils";
import type { DashboardData } from "@/Types";
import { Panel, PanelHeader } from "./Panel";

type Order = DashboardData["recentOrders"][number];

const STATUS: Record<Order["status"], { label: string; cls: string }> = {
  SOLD: { label: "Sold", cls: "bg-brand-strong text-white" },
  RECEIVED: { label: "Received", cls: "bg-brand-strong text-white" },
  PART_RETURNED: { label: "Part returned", cls: "bg-accent text-on-accent" },
  RETURNED: { label: "Returned", cls: "bg-accent text-on-accent" },
  VOID: { label: "Void", cls: "border border-line bg-surface text-ink-2" },
};

const METHOD: Record<string, string> = {
  CASH: "Cash", BANK_TRANSFER: "Bank / card", MOBILE_MONEY: "Mobile money", CREDIT: "On credit", PAYSTACK: "Paystack", EMERGENT: "Emergent",
};

const shortId = (n: string) => n.replace(/^[A-Z0-9]+-/, "").replace(/-0+/, " - ");
const when = (iso?: string) =>
  iso ? new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : "—";

/** The latest sales and purchases, one at a time on a stack of cards (as in the sample). */
export function OrderHistoryCard({ orders, onOpen }: { orders: Order[]; onOpen: (o: Order) => void }) {
  const [i, setI] = useState(0);
  const o = orders[Math.min(i, orders.length - 1)];

  return (
    <Panel className="flex h-full min-h-[24rem] flex-col">
      <PanelHeader title="Order history" subtitle="Latest sales and purchases" href="/sales" linkLabel="Open sales">
        {orders.length > 1 && (
          <div className="flex items-center gap-1">
            <button
              aria-label="Previous order" disabled={i === 0} onClick={() => setI((x) => x - 1)}
              className="flex h-9 w-9 items-center justify-center rounded-xl bg-surface-2 text-ink-2 hover:text-ink disabled:opacity-40"
            ><ChevronLeft className="h-4 w-4" /></button>
            <button
              aria-label="Next order" disabled={i >= orders.length - 1} onClick={() => setI((x) => x + 1)}
              className="flex h-9 w-9 items-center justify-center rounded-xl bg-surface-2 text-ink-2 hover:text-ink disabled:opacity-40"
            ><ChevronRight className="h-4 w-4" /></button>
          </div>
        )}
      </PanelHeader>

      {!o ? (
        <div className="mt-6 flex flex-1 items-center justify-center rounded-3xl bg-surface-2 p-6 text-center text-sm text-ink-3">
          No orders yet. Sales and purchases will appear here.
        </div>
      ) : (
        <div className="relative mt-8 flex flex-1 flex-col">
          {/* Cards peeking from behind */}
          <div className="absolute inset-x-6 -top-4 h-10 rounded-3xl bg-surface-2/60" aria-hidden="true" />
          <div className="absolute inset-x-3 -top-2 h-10 rounded-3xl bg-surface-2" aria-hidden="true" />

          <button
            onClick={() => onOpen(o)}
            className="relative flex flex-1 flex-col rounded-3xl border border-line bg-gradient-to-b from-surface-2 to-surface p-5 text-left transition-shadow hover:shadow-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand"
            aria-label={`Open ${o.invoiceNumber}`}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="text-xs text-ink-3">{o.type === "SALES" ? "Sale" : "Purchase"} · {o.shopName ?? "—"}</div>
                <div className="truncate text-lg font-semibold text-ink">Order {shortId(o.invoiceNumber)}</div>
              </div>
              <span className={cn("shrink-0 rounded-lg px-2.5 py-1 text-xs font-semibold", STATUS[o.status].cls)}>
                {STATUS[o.status].label}
              </span>
            </div>

            <div className="my-5 flex flex-1 items-center justify-between gap-4">
              <div className="min-w-0">
                <div className="text-xl font-semibold leading-tight text-ink line-clamp-2">{o.headline}</div>
                <div className="mt-1 text-sm text-ink-3">
                  {o.itemCount > 1 ? `+${o.itemCount - 1} more item${o.itemCount > 2 ? "s" : ""} · ` : ""}{o.party}
                </div>
              </div>
              <div className={cn(
                "flex h-28 w-28 shrink-0 items-center justify-center rounded-full xl:h-32 xl:w-32",
                o.type === "SALES" ? "fill-speckle bg-brand-soft text-brand/40" : "fill-speckle bg-accent-soft text-accent/40"
              )} aria-hidden="true">
                <span className={cn("flex h-16 w-16 items-center justify-center rounded-full text-white shadow-card", o.type === "SALES" ? "bg-brand" : "bg-accent")}>
                  {o.type === "SALES" ? <ShoppingBag className="h-7 w-7" /> : <Truck className="h-7 w-7" />}
                </span>
              </div>
            </div>

            <dl className="grid grid-cols-2 gap-x-4 gap-y-3 rounded-2xl border border-line bg-surface p-4 pt-3 text-sm">
              <div className="min-w-0">
                <dt className="text-xs text-ink-3">Recorded by</dt>
                <dd className="truncate font-medium text-ink">{o.orderedBy?.split("@")[0] ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-xs text-ink-3">Total</dt>
                <dd className="font-semibold text-ink tabular-nums">{formatCurrency(o.total)}</dd>
              </div>
              <div>
                <dt className="text-xs text-ink-3">Payment</dt>
                <dd className="font-medium text-ink">{METHOD[o.paymentMethod ?? ""] ?? o.paymentMethod ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-xs text-ink-3">Date</dt>
                <dd className="font-medium text-ink">{when(o.createdAt)}</dd>
              </div>
            </dl>
          </button>
          {orders.length > 1 && (
            <div className="mt-3 flex justify-center gap-1.5" aria-hidden="true">
              {orders.map((_, k) => (
                <span key={k} className={cn("h-1.5 rounded-full transition-all", k === i ? "w-5 bg-accent" : "w-1.5 bg-gray-300")} />
              ))}
            </div>
          )}
        </div>
      )}
    </Panel>
  );
}
